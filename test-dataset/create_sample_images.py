import numpy as np
from PIL import Image, ImageDraw

def generate_samples():
    out_dir = r"C:\Users\aleks\.gemini\antigravity-ide\scratch\image-upscaler\test-dataset"

    # 1. Portrait photo (1024x1024 RGB JPEG)
    img1 = Image.new("RGB", (1024, 1024), color=(30, 45, 60))
    draw1 = ImageDraw.Draw(img1)
    for i in range(0, 1024, 64):
        draw1.line([(0, i), (1024, i)], fill=(45, 65, 85), width=2)
        draw1.line([(i, 0), (i, 1024)], fill=(45, 65, 85), width=2)
    draw1.ellipse([256, 256, 768, 768], fill=(220, 160, 120), outline=(255, 200, 150), width=8)
    draw1.ellipse([380, 400, 450, 470], fill=(50, 40, 30))
    draw1.ellipse([574, 400, 644, 470], fill=(50, 40, 30))
    draw1.arc([420, 520, 604, 620], start=20, end=160, fill=(180, 50, 50), width=6)
    img1.save(f"{out_dir}\\test_portrait.jpg", "JPEG", quality=95)

    # 2. Anime illustration with Alpha channel (800x800 RGBA PNG)
    img2 = Image.new("RGBA", (800, 800), color=(0, 0, 0, 0))
    draw2 = ImageDraw.Draw(img2)
    draw2.rectangle([100, 100, 700, 700], fill=(255, 220, 240, 230), outline=(255, 100, 180, 255), width=6)
    draw2.polygon([(400, 150), (600, 500), (200, 500)], fill=(120, 200, 255, 240), outline=(50, 120, 255, 255))
    img2.save(f"{out_dir}\\test_anime.png", "PNG")

    # 3. Landscape photo (1200x800 WEBP)
    img3 = Image.new("RGB", (1200, 800), color=(135, 206, 235))
    draw3 = ImageDraw.Draw(img3)
    # Mountains
    draw3.polygon([(0, 800), (350, 300), (700, 800)], fill=(90, 105, 120))
    draw3.polygon([(400, 800), (800, 250), (1200, 800)], fill=(70, 85, 100))
    # Sun
    draw3.ellipse([100, 80, 240, 220], fill=(255, 215, 0))
    # Foreground
    draw3.rectangle([0, 650, 1200, 800], fill=(34, 139, 34))
    img3.save(f"{out_dir}\\test_landscape.webp", "WEBP", quality=95)

    print("Generated test samples in test-dataset/")

if __name__ == "__main__":
    generate_samples()
