import math
import logging
import torch
import torch.nn.functional as F
import numpy as np
from PIL import Image

logger = logging.getLogger("gpu-worker.tiling")

def upscale_image_tiled(
    model: torch.nn.Module,
    image: Image.Image,
    scale: int = 4,
    tile_size: int = 512,
    tile_padding: int = 32,
    max_retries: int = 3,
    device: str = "cuda"
) -> Image.Image:
    """
    Performs tiled inference with seamless linear blending and automatic CUDA OOM recovery.
    """
    # Check if image has alpha channel
    has_alpha = image.mode == 'RGBA'
    if has_alpha:
        rgb_image = image.convert('RGB')
        alpha_channel = image.split()[3]
    else:
        rgb_image = image.convert('RGB')
        alpha_channel = None

    img_np = np.array(rgb_image).astype(np.float32) / 255.0
    h, w, c = img_np.shape

    # Convert to Tensor (B, C, H, W)
    img_tensor = torch.from_numpy(img_np.transpose((2, 0, 1))).unsqueeze(0)

    current_tile_size = tile_size
    retries = 0

    while retries <= max_retries:
        try:
            logger.info(f"Running tiled inference: {w}x{h} -> {w*scale}x{h*scale} with tile_size={current_tile_size}, padding={tile_padding} on {device}")
            out_tensor = _run_tiles(
                model=model,
                img_tensor=img_tensor,
                scale=scale,
                tile_size=current_tile_size,
                tile_padding=tile_padding,
                device=device
            )
            break
        except (torch.cuda.OutOfMemoryError, RuntimeError) as e:
            err_msg = str(e).lower()
            if "out of memory" in err_msg or "cuda" in err_msg:
                retries += 1
                logger.warning(f"CUDA Out of Memory caught with tile_size={current_tile_size}. Releasing cache and retrying ({retries}/{max_retries})...")
                torch.cuda.empty_cache()
                current_tile_size = max(64, current_tile_size // 2)
                if retries > max_retries:
                    raise RuntimeError(f"CUDA out of memory after {max_retries} retries with tile_size={current_tile_size}: {e}")
            else:
                raise e

    # Convert back to PIL Image
    out_np = out_tensor.squeeze(0).cpu().clamp(0, 1).numpy().transpose((1, 2, 0))
    out_img = Image.fromarray((out_np * 255.0).round().astype(np.uint8), mode='RGB')

    # If alpha channel was present, upscale alpha channel and merge
    if has_alpha and alpha_channel is not None:
        out_w, out_h = out_img.size
        upscaled_alpha = alpha_channel.resize((out_w, out_h), Image.Resampling.LANCZOS)
        out_img.putalpha(upscaled_alpha)

    return out_img


def _run_tiles(
    model: torch.nn.Module,
    img_tensor: torch.Tensor,
    scale: int,
    tile_size: int,
    tile_padding: int,
    device: str
) -> torch.Tensor:
    b, c, h, w = img_tensor.shape
    out_h, out_w = h * scale, w * scale

    # If the image is smaller than tile_size, run directly
    if h <= tile_size and w <= tile_size:
        with torch.no_grad():
            inp = img_tensor.to(device)
            out = model(inp)
            return out.cpu()

    # Tiled processing with seamless overlap accumulation
    output = torch.zeros((b, c, out_h, out_w), dtype=torch.float32)
    weights = torch.zeros((b, 1, out_h, out_w), dtype=torch.float32)

    stride = tile_size - (tile_padding * 2)
    if stride <= 0:
        stride = tile_size // 2

    y_steps = math.ceil(h / stride)
    x_steps = math.ceil(w / stride)

    with torch.no_grad():
        for yi in range(y_steps):
            y0 = yi * stride
            y1 = min(y0 + tile_size, h)
            if y1 - y0 < tile_size and y0 > 0:
                y0 = max(0, y1 - tile_size)

            for xi in range(x_steps):
                x0 = xi * stride
                x1 = min(x0 + tile_size, w)
                if x1 - x0 < tile_size and x0 > 0:
                    x0 = max(0, x1 - tile_size)

                # Extract tile
                tile = img_tensor[:, :, y0:y1, x0:x1].to(device)

                # Process tile through model
                tile_out = model(tile).cpu()

                # Generate 2D blend weights for seamless transition
                th, tw = y1 - y0, x1 - x0
                out_th, out_tw = th * scale, tw * scale

                wx = _create_blend_window_1d(out_tw)
                wy = _create_blend_window_1d(out_th)
                tile_weight = torch.outer(wy, wx).unsqueeze(0).unsqueeze(0)

                # Accumulate
                out_y0, out_y1 = y0 * scale, y1 * scale
                out_x0, out_x1 = x0 * scale, x1 * scale

                output[:, :, out_y0:out_y1, out_x0:out_x1] += tile_out * tile_weight
                weights[:, :, out_y0:out_y1, out_x0:out_x1] += tile_weight

                del tile, tile_out
                if device == "cuda":
                    torch.cuda.empty_cache()

    # Normalize by accumulated weights
    weights = torch.clamp(weights, min=1e-5)
    return output / weights


def _create_blend_window_1d(length: int) -> torch.Tensor:
    """Creates a 1D linear/cosine taper window for seam blending."""
    if length <= 2:
        return torch.ones(length, dtype=torch.float32)
    # Cosine window taper for zero seam visibility
    n = torch.arange(length, dtype=torch.float32)
    w = 0.5 - 0.5 * torch.cos(2 * math.pi * (n + 0.5) / length)
    return torch.clamp(w, min=0.1)
