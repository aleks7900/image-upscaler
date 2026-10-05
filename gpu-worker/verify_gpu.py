import torch
import sys

def main():
    print(f"Python version: {sys.version}")
    print(f"PyTorch version: {torch.__version__}")
    cuda_available = torch.cuda.is_available()
    print(f"CUDA available: {cuda_available}")
    if cuda_available:
        device_count = torch.cuda.device_count()
        print(f"Device count: {device_count}")
        device_name = torch.cuda.get_device_name(0)
        print(f"Device name: {device_name}")
        free_bytes, total_bytes = torch.cuda.mem_get_info(0)
        print(f"Total VRAM: {total_bytes / (1024**2):.1f} MB")
        print(f"Free VRAM: {free_bytes / (1024**2):.1f} MB")
        print(f"CUDA version: {torch.version.cuda}")
        # Run a small tensor computation on GPU
        x = torch.randn(1000, 1000, device="cuda")
        y = torch.matmul(x, x)
        print(f"GPU tensor test success: sum={y.sum().item():.2f}")
    else:
        print("CUDA is NOT available.")

if __name__ == "__main__":
    main()
