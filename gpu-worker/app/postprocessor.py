import io
import math
import logging
from pathlib import Path
from PIL import Image, ImageCms

logger = logging.getLogger("gpu-worker.postprocessor")

SRGB_PROFILE = ImageCms.createProfile("sRGB")

def process_for_adobe_stock(
    image: Image.Image,
    target_path: Path,
    initial_quality: int = 95,
    min_quality: int = 85,
    max_megapixels: float = 100.0,
    min_megapixels: float = 4.0,
    max_file_size_bytes: int = 45 * 1024 * 1024,
    output_format: str = "JPEG"
) -> dict:
    """
    Transforms upscaled PIL Image into an Adobe Stock ready asset:
    1. Converts color profile to sRGB (ICC profile transformation).
    2. Proportional scale-down if theoretical resolution exceeds maximum megapixels (100 MP).
    3. Intelligent iterative JPEG quality reduction if file size exceeds 45 MB.
    4. Validates technical criteria (format, sRGB, MP bounds, file size).
    """
    # 1. Color Profile Management -> sRGB conversion
    converted_img = _convert_to_srgb(image)

    # 2. Maximum Resolution Protection
    cur_w, cur_h = converted_img.size
    cur_mp = (cur_w * cur_h) / 1_000_000.0

    if cur_mp > max_megapixels:
        ratio = math.sqrt(max_megapixels / cur_mp)
        target_w = max(1, int(cur_w * ratio))
        target_h = max(1, int(cur_h * ratio))
        logger.info(f"Image resolution {cur_w}x{cur_h} ({cur_mp:.2f} MP) exceeds {max_megapixels} MP limit. Proporationally scaling down to {target_w}x{target_h} ({target_w*target_h/1e6:.2f} MP)")
        converted_img = converted_img.resize((target_w, target_h), Image.Resampling.LANCZOS)
        cur_w, cur_h = converted_img.size
        cur_mp = (cur_w * cur_h) / 1_000_000.0

    # 3. Intelligent Encoding (Iterative Quality Check for <= 45 MB)
    target_path.parent.mkdir(parents=True, exist_ok=True)
    current_q = initial_quality
    encoded_bytes = None

    # Quality reduction sequence
    quality_steps = [initial_quality, 92, 90, 88, 85]
    if initial_quality not in quality_steps:
        quality_steps.insert(0, initial_quality)

    icc_bytes = ImageCms.ImageCmsProfile(SRGB_PROFILE).tobytes()

    for q in quality_steps:
        if q < min_quality:
            break
        buf = io.BytesIO()
        if output_format.upper() in ["JPEG", "JPG"]:
            # Ensure RGB mode for JPEG
            if converted_img.mode != "RGB":
                save_img = converted_img.convert("RGB")
            else:
                save_img = converted_img
            save_img.save(buf, format="JPEG", quality=q, subsampling=0, icc_profile=icc_bytes)
        elif output_format.upper() == "PNG":
            converted_img.save(buf, format="PNG", icc_profile=icc_bytes)
        elif output_format.upper() == "WEBP":
            converted_img.save(buf, format="WEBP", quality=q, icc_profile=icc_bytes)
        else:
            save_img = converted_img.convert("RGB")
            save_img.save(buf, format="JPEG", quality=q, icc_profile=icc_bytes)

        size = buf.tell()
        current_q = q
        encoded_bytes = buf.getvalue()

        if size <= max_file_size_bytes or output_format.upper() == "PNG":
            break
        logger.info(f"File size at quality {q} is {size / (1024*1024):.2f} MB (> 45 MB). Reducing quality...")

    # Write final bytes to target file
    with open(target_path, "wb") as f:
        f.write(encoded_bytes)

    final_size = target_path.stat().st_size
    megapixels = round((cur_w * cur_h) / 1_000_000.0, 2)

    # Technical validation
    stock_ready = (
        megapixels >= min_megapixels and
        megapixels <= max_megapixels and
        final_size <= max_file_size_bytes and
        output_format.upper() in ["JPEG", "JPG"]
    )

    return {
        "output_width": cur_w,
        "output_height": cur_h,
        "output_megapixels": megapixels,
        "output_size": final_size,
        "output_format": output_format.upper(),
        "color_profile": "sRGB",
        "final_quality": current_q,
        "stock_ready": stock_ready
    }


def _convert_to_srgb(image: Image.Image) -> Image.Image:
    """Converts image to sRGB using ICC profile transform if embedded."""
    icc_profile = image.info.get("icc_profile")
    if icc_profile:
        try:
            input_profile = ImageCms.ImageCmsProfile(io.BytesIO(icc_profile))
            transformed = ImageCms.profileToProfile(
                image,
                input_profile,
                SRGB_PROFILE,
                outputMode='RGB' if image.mode != 'RGBA' else 'RGBA'
            )
            return transformed
        except Exception as e:
            logger.warning(f"Could not convert input ICC profile: {e}. Falling back to standard RGB conversion.")

    if image.mode not in ["RGB", "RGBA"]:
        return image.convert("RGB")
    return image
