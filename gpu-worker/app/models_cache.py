import os
import sys
import logging
import requests
import torch
from pathlib import Path
from app.engine import create_model

logger = logging.getLogger("gpu-worker.cache")

MODEL_URLS = {
    ("general", 4): {
        "filename": "RealESRGAN_x4plus.pth",
        "url": "https://github.com/xinntao/Real-ESRGAN/releases/download/v0.1.0/RealESRGAN_x4plus.pth",
        "native_scale": 4
    },
    ("anime", 4): {
        "filename": "RealESRGAN_x4plus_anime_6B.pth",
        "url": "https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.2.4/RealESRGAN_x4plus_anime_6B.pth",
        "native_scale": 4
    },
    ("general", 2): {
        "filename": "RealESRGAN_x2plus.pth",
        "url": "https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.1/RealESRGAN_x2plus.pth",
        "native_scale": 2
    },
    ("anime", 2): {
        "filename": "RealESRGAN_x4plus_anime_6B.pth",
        "url": "https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.2.4/RealESRGAN_x4plus_anime_6B.pth",
        "native_scale": 4
    }
}

class ModelManager:
    def __init__(self, cache_dir: str = "/cache"):
        self.cache_dir = Path(os.environ.get("MODEL_CACHE_DIR", cache_dir))
        self.cache_dir.mkdir(parents=True, exist_ok=True)
        self.loaded_models = {}
        self.current_loaded_key = None

    def get_model(self, model_name: str = "general", scale: int = 4, device: str = "cuda") -> torch.nn.Module:
        key = (model_name.lower(), scale)
        if key not in MODEL_URLS:
            # Fallback to general 4x
            key = ("general", 4)

        if key in self.loaded_models:
            return self.loaded_models[key]

        meta = MODEL_URLS[key]
        weight_path = self.cache_dir / meta["filename"]

        if not weight_path.exists() or weight_path.stat().st_size < 1_000_000:
            self._download_weights(meta["url"], weight_path)

        logger.info(f"Instantiating model {key} on device {device}...")
        native_scale = meta.get("native_scale", key[1])
        model = create_model(model_name=key[0], scale=native_scale, device=device)

        if weight_path.exists() and weight_path.stat().st_size > 1_000_000:
            try:
                logger.info(f"Loading weights from {weight_path}...")
                state_dict = torch.load(str(weight_path), map_location=device, weights_only=False)
                if "params_ema" in state_dict:
                    state_dict = state_dict["params_ema"]
                elif "params" in state_dict:
                    state_dict = state_dict["params"]
                model.load_state_dict(state_dict, strict=False)
                logger.info(f"Successfully loaded weights for {key} into VRAM.")
            except Exception as e:
                logger.warning(f"Failed to load checkpoint from {weight_path}: {e}. Proceeding with initialized model.")
        else:
            logger.warning(f"Weights file not found or incomplete: {weight_path}. Using initialized architecture.")

        self.loaded_models[key] = model
        self.current_loaded_key = f"{key[0]}_{key[1]}x"
        return model

    def _download_weights(self, url: str, target_path: Path):
        logger.info(f"Downloading model weights from {url} to {target_path}...")
        temp_path = target_path.with_suffix(".tmp")
        try:
            with requests.get(url, stream=True, timeout=120) as r:
                r.raise_for_status()
                with open(temp_path, "wb") as f:
                    for chunk in r.iter_content(chunk_size=8192 * 16):
                        if chunk:
                            f.write(chunk)
            temp_path.replace(target_path)
            logger.info(f"Downloaded model weights to {target_path} ({target_path.stat().st_size / (1024*1024):.1f} MB)")
        except Exception as e:
            logger.warning(f"Failed to download weights from {url}: {e}")
            if temp_path.exists():
                temp_path.unlink()

model_manager = ModelManager()
