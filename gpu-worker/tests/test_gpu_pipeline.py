import sys
import torch
import numpy as np
from PIL import Image
from pathlib import Path

from app.engine import create_model, RRDBNet
from app.tiling import _create_blend_window_1d, upscale_image_tiled
from app.postprocessor import process_for_adobe_stock

def test_cuda_available():
    print("Testing CUDA availability...")
    assert torch.cuda.is_available(), "CUDA must be available"
    device_name = torch.cuda.get_device_name(0)
    print(f"PASS: CUDA available on {device_name}")

def test_model_forward_pass():
    print("Testing RRDBNet forward pass on CUDA...")
    device = "cuda" if torch.cuda.is_available() else "cpu"
    model = create_model("general", scale=4, device=device)
    dummy_input = torch.randn(1, 3, 64, 64, device=device)
    with torch.no_grad():
        out = model(dummy_input)
    assert out.shape == (1, 3, 256, 256), f"Expected (1, 3, 256, 256), got {out.shape}"
    print(f"PASS: Forward pass output shape: {out.shape}")

def test_blend_window():
    print("Testing 2D cosine blend window...")
    w = _create_blend_window_1d(64)
    assert len(w) == 64
    assert w[0] > 0 and w[-1] > 0
    assert torch.is_tensor(w)
    print("PASS: Blend window created successfully")

def test_postprocess_adobe_stock_limits():
    print("Testing Adobe Stock post-processing constraints...")
    # Create an image of 12000 x 9000 (108 MP) -> must be scaled down to <= 100 MP
    img = Image.new("RGB", (12000, 9000), color=(100, 150, 200))
    out_path = Path("/tmp/test_stock.jpg")
    
    result = process_for_adobe_stock(
        image=img,
        target_path=out_path,
        initial_quality=95,
        min_quality=85,
        max_megapixels=100.0,
        min_megapixels=4.0,
        max_file_size_bytes=45 * 1024 * 1024,
        output_format="JPEG"
    )
    
    out_w = result["output_width"]
    out_h = result["output_height"]
    out_mp = result["output_megapixels"]
    out_size = result["output_size"]
    profile = result["color_profile"]
    ready = result["stock_ready"]

    print(f"Postprocessed: {out_w}x{out_h} ({out_mp:.2f} MP, {out_size} bytes, {profile}, ready: {ready})")
    assert out_mp <= 100.0, f"Megapixels {out_mp} exceeded 100 MP limit!"
    assert out_size <= 45 * 1024 * 1024, f"File size {out_size} exceeded 45 MB limit!"
    assert profile == "sRGB", f"Profile was not sRGB: {profile}"
    assert ready is True, "Image should be ready"
    
    if out_path.exists():
        out_path.unlink()
    print("PASS: Adobe Stock max-resolution and size constraints enforced perfectly!")

if __name__ == "__main__":
    test_cuda_available()
    test_model_forward_pass()
    test_blend_window()
    test_postprocess_adobe_stock_limits()
    print("\nAll GPU Worker tests passed successfully!")
