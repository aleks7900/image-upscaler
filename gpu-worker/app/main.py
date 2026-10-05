import os
import sys
import time
import logging
from pathlib import Path
from typing import Optional
from fastapi import FastAPI, HTTPException, status
from pydantic import BaseModel, Field
from PIL import Image
import torch

from app.models_cache import model_manager
from app.tiling import upscale_image_tiled
from app.postprocessor import process_for_adobe_stock

logging.basicConfig(
    level=logging.INFO,
    format='{"time":"%(asctime)s", "level":"%(levelname)s", "logger":"%(name)s", "message":"%(message)s"}'
)
logger = logging.getLogger("gpu-worker")

app = FastAPI(title="NVIDIA CUDA GPU Upscale Worker", version="1.0.0")

active_jobs_count = 0
queued_jobs_count = 0

class UpscaleRequestPayload(BaseModel):
    image_id: Optional[str] = Field(None, alias="imageId")
    batch_id: Optional[str] = Field(None, alias="batchId")
    input_path: str = Field(..., alias="inputPath")
    output_path: str = Field(..., alias="outputPath")
    scale: int = 4
    model: str = "general"
    output_format: str = Field("JPEG", alias="outputFormat")
    quality: int = 95
    preset: str = "ADOBE_STOCK"
    tile_size: int = Field(512, alias="tileSize")
    tile_padding: int = Field(32, alias="tilePadding")
    max_retries: int = Field(3, alias="maxRetries")

    class Config:
        populate_by_name = True


@app.get("/health")
def health_check():
    cuda_ok = torch.cuda.is_available()
    if not cuda_ok:
        logger.error("Health check failed: CUDA is NOT available!")
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="CUDA is not available on this worker"
        )
    return {
        "status": "UP",
        "cuda_available": True,
        "device": torch.cuda.get_device_name(0),
        "model_loaded": model_manager.current_loaded_key is not None
    }


@app.get("/gpu/info")
def get_gpu_info():
    cuda_available = torch.cuda.is_available()
    device_name = "N/A"
    total_vram_mb = 0
    free_vram_mb = 0
    cuda_ver = torch.version.cuda if hasattr(torch.version, 'cuda') else "N/A"

    if cuda_available:
        try:
            device_name = torch.cuda.get_device_name(0)
            free_b, total_b = torch.cuda.mem_get_info(0)
            free_vram_mb = int(free_b / (1024 * 1024))
            total_vram_mb = int(total_b / (1024 * 1024))
        except Exception as e:
            logger.warning(f"Error querying GPU memory info: {e}")

    return {
        "available": cuda_available,
        "workerReady": True,
        "device": device_name,
        "cudaAvailable": cuda_available,
        "cudaVersion": str(cuda_ver),
        "pytorchVersion": str(torch.__version__),
        "totalVramMb": total_vram_mb,
        "freeVramMb": free_vram_mb,
        "modelLoaded": model_manager.current_loaded_key is not None,
        "loadedModel": model_manager.current_loaded_key or "none",
        "activeJobs": active_jobs_count,
        "queuedJobs": queued_jobs_count
    }


@app.post("/upscale")
def process_upscale(req: UpscaleRequestPayload):
    global active_jobs_count, queued_jobs_count
    start_time = time.time()
    active_jobs_count += 1

    input_file = Path(req.input_path)
    output_file = Path(req.output_path)

    logger.info(f"Processing upscale: image={req.image_id}, batch={req.batch_id}, scale={req.scale}x, model={req.model}, input={input_file}")

    if not input_file.exists():
        active_jobs_count = max(0, active_jobs_count - 1)
        return {
            "success": False,
            "error": f"Input file not found: {req.input_path}"
        }

    device = "cuda" if torch.cuda.is_available() else "cpu"
    if device == "cpu":
        active_jobs_count = max(0, active_jobs_count - 1)
        return {
            "success": False,
            "error": "CUDA GPU is not available for inference."
        }

    try:
        # Load image
        with Image.open(input_file) as img:
            img.load()
            orig_w, orig_h = img.size

            # Load / retrieve model
            model = model_manager.get_model(
                model_name=req.model,
                scale=req.scale,
                device=device
            )

            # Run tiled CUDA inference with OOM recovery
            upscaled_pil = upscale_image_tiled(
                model=model,
                image=img,
                scale=req.scale,
                tile_size=req.tile_size,
                tile_padding=req.tile_padding,
                max_retries=req.max_retries,
                device=device
            )

        # Post-process for Adobe Stock / Target format
        post_info = process_for_adobe_stock(
            image=upscaled_pil,
            target_path=output_file,
            initial_quality=req.quality,
            output_format=req.output_format
        )

        duration_ms = int((time.time() - start_time) * 1000)

        logger.info(f"Successfully processed image {req.image_id} in {duration_ms}ms (output: {post_info['output_width']}x{post_info['output_height']}, size: {post_info['output_size']} bytes)")

        return {
            "success": True,
            "output_width": post_info["output_width"],
            "output_height": post_info["output_height"],
            "output_megapixels": post_info["output_megapixels"],
            "output_size": post_info["output_size"],
            "output_format": post_info["output_format"],
            "color_profile": post_info["color_profile"],
            "processing_time_ms": duration_ms,
            "stock_ready": post_info["stock_ready"],
            "retries_used": 0,
            "final_tile_size": req.tile_size
        }

    except Exception as e:
        logger.error(f"Inference failed for image {req.image_id}: {e}", exc_info=True)
        return {
            "success": False,
            "error": str(e)
        }
    finally:
        active_jobs_count = max(0, active_jobs_count - 1)
