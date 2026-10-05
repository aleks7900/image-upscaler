# CUDA Batch Image Upscaler for Adobe Stock

A production-grade, fully containerized batch image super-resolution platform optimized for **NVIDIA CUDA GPU acceleration** and automated **Adobe Stock submission compliance**.

The application orchestrates batch upscaling of hundreds of images across a React + Vite frontend, Spring Boot orchestrator, PostgreSQL state store, and persistent Python CUDA GPU worker with PyTorch super-resolution models (Real-ESRGAN).

---

## 1. System Architecture

```text
                                Docker Compose
                                      │
            ┌─────────────────────────┼─────────────────────────┐
            │                         │                         │
            ▼                         ▼                         ▼
     ┌─────────────┐           ┌──────────────┐          ┌───────────────┐
     │  Frontend   │           │ Spring Boot  │          │  PostgreSQL   │
     │ React+Vite  │──────────▶│   Backend    │─────────▶│   Database    │
     │    Nginx    │           │   (Java 21)  │          │    (v17)      │
     └─────────────┘           └──────┬───────┘          └───────────────┘
            ▲                         │
            │                         ▼
            │                  ┌───────────────┐
            │                  │   CUDA GPU    │
            │                  │    Worker     │
            │                  │Python+PyTorch │
            │                  └──────┬────────┘
            │                         │
            │                         ▼
            │                    NVIDIA GPU
            │               (Blackwell / Ada /
            │                 Ampere / Turing)
            │
            └───────── Server-Sent Events (SSE) ─── Live Progress Stream

                              Shared Docker Volume
                                       │
                              ┌────────┴────────┐
                              ▼                 ▼
                           uploads           results
```

### Key Services

| Service | Port | Description |
| :--- | :--- | :--- |
| **`frontend`** | `80` (or `3000`) | React 18 + TypeScript + Vite served via production Nginx. Handles drag-and-drop, configuration, live SSE progress reporting, comparison sliders, and ZIP streaming. |
| **`backend`** | `8080` | Spring Boot 3.3 (Java 21) REST orchestration, image validation (Apache Tika magic bytes), persistent queue, bounded GPU concurrency, and streaming ZIP downloads. |
| **`gpu-worker`**| `8000` | Persistent Python FastAPI daemon running PyTorch with native CUDA 12.8 / 12.4 acceleration, tiled inference, OOM recovery, and Adobe Stock color management. |
| **`postgres`** | `5432` | PostgreSQL 17 relational database persisting batch and per-image execution states with Flyway migrations. |

---

## 2. Adobe Stock Ready Pipeline

The application features a dedicated **`ADOBE_STOCK`** preset enforcing all technical submission requirements:

```text
Upload Images (JPG, PNG, WEBP)
     ↓
Tika Binary Magic Bytes & Decompression Bomb Validation
     ↓
Persistent PostgreSQL Batch & Image State Queue
     ↓
Real-ESRGAN CUDA Inference (2x / 4x) with Tiled Blending
     ↓
Adobe Stock Post-Processing:
  ├─ Color Management: Conversion to sRGB IEC61966-2.1 with embedded ICC profile
  ├─ Maximum Resolution Protection: Proportional scale-down if theoretical upscale > 100 MP
  └─ Intelligent JPEG Encoding: Iterative quality reduction (95→92→90→88→85) if file size > 45 MB
     ↓
Technical Compliance Audit:
  ✓ Format: JPEG
  ✓ Color Profile: sRGB
  ✓ Resolution: 4.0 MP – 100.0 MP
  ✓ File Size: < 45 MB
     ↓
Live Preview & Results ZIP Stream (with adobe_stock_manifest.csv)
```

---

## 3. Host Requirements

**Critical constraint: Every application component runs inside Docker.**

The host machine **only** requires:
- [Docker Engine](https://docs.docker.com/engine/install/) & [Docker Compose](https://docs.docker.com/compose/) (v2.20+)
- NVIDIA GPU Driver (v535+)
- [NVIDIA Container Toolkit](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/latest/install-guide.html) (for GPU passthrough)

> **No host dependencies required:** Java, Maven, Node.js, npm, Python, pip, PostgreSQL, PyTorch, or CUDA Toolkit are **not** needed on the host machine.

---

## 4. Quick Start

### 1. Clone the repository and configure environment

```bash
git clone <repository-url>
cd image-upscaler

# Copy environment template
cp .env.example .env
```

### 2. Launch the entire application

```bash
docker compose up -d --build
```

### 3. Verify container health

```bash
docker compose ps
```

All 4 services (`upscaler-frontend`, `upscaler-backend`, `upscaler-gpu-worker`, `upscaler-postgres`) will report `healthy`.

### 4. Open the Web Application

Visit **[http://localhost](http://localhost)** in your browser.

---

## 5. Verifying CUDA GPU Access

### Check NVIDIA GPU detection inside container

```bash
docker compose exec gpu-worker nvidia-smi
```

Expected output:
```text
+-----------------------------------------------------------------------------------------+
| NVIDIA-SMI 615.71.08              KMD Version: 616.92        CUDA UMD Version: 13.4     |
| GPU  Name                 Persistence-M | Bus-Id          Disp.A | Volatile Uncorr. ECC |
|   0  NVIDIA GeForce RTX ...         On  |   00000000:02:00.0  On |                  N/A |
+-----------------------------------------------------------------------------------------+
```

### Verify PyTorch CUDA Acceleration

```bash
docker compose exec gpu-worker python3 -c "import torch; print('CUDA Available:', torch.cuda.is_available()); print('Device:', torch.cuda.get_device_name(0)); print('Memory:', torch.cuda.mem_get_info(0))"
```

Expected output:
```text
CUDA Available: True
Device: NVIDIA GeForce RTX 5080 Laptop GPU (or your installed GPU)
Memory: (15798206464, 17066065920)
```

---

## 6. Large Image Tiled Inference & OOM Recovery

Super-resolution of high-resolution stock images (e.g. 24 MP upscaled 4x to 384 MP) requires substantial VRAM. The GPU worker implements:

1. **Cosine Window Blending**:
   - Divides images into tiles (`GPU_TILE_SIZE: 512`) with overlap padding (`GPU_TILE_PADDING: 32`).
   - Generates 2D cosine blending weights across overlap zones so tiles fuse seamlessly with zero edge lines or stitching seams.
2. **Automatic CUDA OOM Recovery**:
   - Catches `torch.cuda.OutOfMemoryError` gracefully without worker termination.
   - Flushes recoverable GPU memory cache (`torch.cuda.empty_cache()`).
   - Halves tile size (e.g., `512` → `256` → `128`) and retries up to `GPU_MAX_RETRIES: 3`.
   - If an image cannot be processed under minimum thresholds, marks the image `FAILED` while continuing the remainder of the batch uninterrupted.

---

## 7. REST API Endpoints

### Batch Management
- `POST /api/v1/upscale/batches` — Create new upscale batch
- `POST /api/v1/upscale/batches/{batchId}/images` — Upload multiple images (multipart)
- `POST /api/v1/upscale/batches/{batchId}/start` — Start batch execution
- `POST /api/v1/upscale/batches/{batchId}/cancel` — Cancel batch execution
- `GET /api/v1/upscale/batches` — List all batches
- `GET /api/v1/upscale/batches/{batchId}` — Get batch status & counters
- `GET /api/v1/upscale/batches/{batchId}/images` — List batch images
- `GET /api/v1/upscale/batches/{batchId}/results.zip` — Stream results ZIP archive with manifest CSV
- `GET /api/v1/upscale/batches/{batchId}/events` — Server-Sent Events (SSE) live progress stream

### Image Actions
- `GET /api/v1/upscale/images/{imageId}` — Get image details
- `POST /api/v1/upscale/images/{imageId}/retry` — Re-queue a failed image
- `GET /api/v1/upscale/images/{imageId}/input` — Stream original uploaded file
- `GET /api/v1/upscale/images/{imageId}/result` — Stream processed result file

### System & Health
- `GET /api/v1/system/gpu` — Real-time GPU name, VRAM (total/free), CUDA version, queue length
- `GET /api/v1/system/health` — Worker readiness status
- `GET /actuator/health` — Spring Boot Actuator comprehensive healthcheck
- `GET /actuator/metrics` — Performance and counter metrics

---

## 8. Stopping and Managing

### Stop application

```bash
docker compose down
```

### View live logs

```bash
docker compose logs -f
```

### View GPU worker logs specifically

```bash
docker compose logs -f gpu-worker
```

### Restart without losing data

```bash
docker compose restart
```
On boot, the backend automatically reconciles in-flight jobs and re-queues any interrupted assets.

---

## 9. Troubleshooting

### 1. Docker cannot see GPU / `nvidia-smi` fails inside container
- Verify that NVIDIA Container Toolkit is installed on the host:
  ```bash
  nvidia-ctk --version
  ```
- Ensure Docker daemon is configured with the NVIDIA runtime (`/etc/docker/daemon.json`):
  ```json
  {
    "default-runtime": "nvidia",
    "runtimes": {
      "nvidia": {
        "path": "nvidia-container-runtime",
        "runtimeArgs": []
      }
    }
  }
  ```
- Restart Docker service (`sudo systemctl restart docker` or restart Docker Desktop).

### 2. `torch.cuda.is_available() == False`
- Verify that the host driver version supports your installed GPU architecture.
- For newest NVIDIA RTX 50-series (Blackwell `sm_120`), the worker image installs PyTorch cu128 which natively supports compute capability 12.0.

### 3. CUDA Out of Memory (OOM)
- If handling ultra-high-resolution images (> 50 MP inputs), set a smaller tile size in `.env`:
  ```env
  GPU_TILE_SIZE=256
  GPU_TILE_PADDING=16
  ```
- Ensure `GPU_CONCURRENCY=1` so multiple models or images do not execute simultaneously in VRAM.

### 4. Backend cannot reach GPU worker
- Verify `gpu-worker` container is healthy (`docker compose ps`).
- Check internal networking: `docker compose exec backend ping gpu-worker`.
