import json
import time
import requests
from pathlib import Path

BASE_URL = "http://localhost/api/v1"
TEST_IMG = Path("test-dataset/test_portrait.jpg")

def run_e2e_test():
    print(f"--- 1. Checking GPU Status via Nginx ---")
    gpu_resp = requests.get(f"{BASE_URL}/system/gpu")
    assert gpu_resp.status_code == 200, f"Failed GPU status: {gpu_resp.text}"
    gpu_info = gpu_resp.json()
    print("GPU Info:", json.dumps(gpu_info, indent=2))
    assert gpu_info["cudaAvailable"] is True, "CUDA is not available!"
    assert "RTX" in gpu_info["device"], f"Unexpected device: {gpu_info['device']}"

    print("\n--- 2. Creating Adobe Stock Batch ---")
    batch_payload = {
        "preset": "ADOBE_STOCK",
        "scale": 4,
        "model": "general",
        "outputFormat": "JPEG",
        "quality": 95
    }
    batch_resp = requests.post(f"{BASE_URL}/upscale/batches", json=batch_payload)
    assert batch_resp.status_code == 200, f"Failed to create batch: {batch_resp.text}"
    batch = batch_resp.json()
    batch_id = batch["id"]
    print(f"Created Batch ID: {batch_id}, Status: {batch['status']}")

    print("\n--- 3. Uploading Test Image (1024x1024 RGB) ---")
    with open(TEST_IMG, "rb") as f:
        files = {"files": ("test_portrait.jpg", f, "image/jpeg")}
        upload_resp = requests.post(f"{BASE_URL}/upscale/batches/{batch_id}/images", files=files)
    assert upload_resp.status_code == 200, f"Upload failed: {upload_resp.text}"
    images = upload_resp.json()
    image_id = images[0]["id"]
    print(f"Uploaded Image ID: {image_id}, Name: {images[0]['originalFilename']}")
    print(f"Input: {images[0]['inputWidth']}x{images[0]['inputHeight']} ({images[0]['inputMegapixels']} MP)")

    print("\n--- 4. Starting Batch Processing ---")
    start_resp = requests.post(f"{BASE_URL}/upscale/batches/{batch_id}/start")
    assert start_resp.status_code == 200, f"Start failed: {start_resp.text}"
    print(f"Batch started: {start_resp.json()['status']}")

    print("\n--- 5. Monitoring GPU Processing ---")
    max_wait = 60
    start_time = time.time()
    completed = False

    while time.time() - start_time < max_wait:
        status_resp = requests.get(f"{BASE_URL}/upscale/batches/{batch_id}")
        assert status_resp.status_code == 200
        b_data = status_resp.json()
        print(f"Batch status: {b_data['status']}, completed: {b_data['completedImages']}/{b_data['totalImages']}")
        
        if b_data["status"] in ["COMPLETED", "PARTIALLY_COMPLETED", "FAILED"]:
            completed = True
            break
        time.sleep(2)

    assert completed, "Batch processing timed out!"
    assert b_data["status"] == "COMPLETED", f"Batch did not complete successfully: {b_data['status']}"
    assert b_data["completedImages"] == 1, f"Expected 1 completed image, got {b_data['completedImages']}"

    print("\n--- 6. Verifying Adobe Stock Output ---")
    img_resp = requests.get(f"{BASE_URL}/upscale/images/{image_id}")
    assert img_resp.status_code == 200
    img_data = img_resp.json()
    print("Processed Image Details:", json.dumps(img_data, indent=2))
    
    assert img_data["status"] == "COMPLETED", f"Image status: {img_data['status']}"
    assert img_data["outputWidth"] == 4096, f"Expected 4096 width, got {img_data['outputWidth']}"
    assert img_data["outputHeight"] == 4096, f"Expected 4096 height, got {img_data['outputHeight']}"
    assert img_data["outputMegapixels"] >= 16.7, f"Expected >= 16.7 MP, got {img_data['outputMegapixels']}"
    assert img_data["stockReady"] is True, "Expected stockReady to be True"
    assert img_data["outputFormat"] == "JPEG", f"Expected JPEG, got {img_data['outputFormat']}"
    assert "sRGB" in img_data["colorProfile"], f"Expected sRGB, got {img_data['colorProfile']}"

    print("\n--- 7. Downloading Processed Result Image ---")
    dl_resp = requests.get(f"{BASE_URL}/upscale/images/{image_id}/result")
    assert dl_resp.status_code == 200, f"Download failed: {dl_resp.status_code}"
    print(f"Downloaded result image size: {len(dl_resp.content)} bytes, Content-Type: {dl_resp.headers.get('Content-Type')}")

    print("\n--- 8. Downloading Batch Results ZIP ---")
    zip_resp = requests.get(f"{BASE_URL}/upscale/batches/{batch_id}/results.zip")
    assert zip_resp.status_code == 200, f"ZIP download failed: {zip_resp.status_code}"
    print(f"Downloaded ZIP size: {len(zip_resp.content)} bytes")
    
    import zipfile
    import io
    with zipfile.ZipFile(io.BytesIO(zip_resp.content)) as z:
        namelist = z.namelist()
        print(f"ZIP contents: {namelist}")
        assert "adobe_stock_manifest.csv" in namelist, "Manifest CSV missing from ZIP!"
        manifest_text = z.read("adobe_stock_manifest.csv").decode("utf-8")
        print("Manifest CSV content:\n" + manifest_text)
        assert "test_portrait_upscaled.jpg" in namelist or any(n.endswith(".jpg") for n in namelist)

    print("\n==============================================")
    print("SUCCESS: Full End-to-End CUDA Adobe Stock test PASSED!")
    print("==============================================")

if __name__ == "__main__":
    run_e2e_test()
