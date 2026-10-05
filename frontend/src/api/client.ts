import { BatchCreateRequest, BatchResponse, GpuStatusResponse, ImageResponse } from '../types';

const API_BASE = '/api/v1';

export async function fetchGpuStatus(): Promise<GpuStatusResponse> {
  const res = await fetch(`${API_BASE}/system/gpu`);
  if (!res.ok) {
    throw new Error(`Failed to fetch GPU status: ${res.statusText}`);
  }
  return res.json();
}

export async function createBatch(data: BatchCreateRequest): Promise<BatchResponse> {
  const res = await fetch(`${API_BASE}/upscale/batches`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || `Failed to create batch: ${res.statusText}`);
  }
  return res.json();
}

export async function uploadBatchImages(
  batchId: string,
  files: File[],
  onProgress?: (percent: number) => void
): Promise<ImageResponse[]> {
  const formData = new FormData();
  for (const file of files) {
    formData.append('files', file);
  }

  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', `${API_BASE}/upscale/batches/${batchId}/images`);

    if (xhr.upload && onProgress) {
      xhr.upload.onprogress = (event) => {
        if (event.lengthComputable) {
          const percent = Math.round((event.loaded / event.total) * 100);
          onProgress(percent);
        }
      };
    }

    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          const json = JSON.parse(xhr.responseText);
          resolve(json);
        } catch (e) {
          reject(e);
        }
      } else {
        try {
          const json = JSON.parse(xhr.responseText);
          reject(new Error(json.message || `Upload failed: ${xhr.statusText}`));
        } catch {
          reject(new Error(`Upload failed: ${xhr.statusText}`));
        }
      }
    };

    xhr.onerror = () => reject(new Error('Network error during upload'));
    xhr.send(formData);
  });
}

export async function startBatch(batchId: string): Promise<BatchResponse> {
  const res = await fetch(`${API_BASE}/upscale/batches/${batchId}/start`, {
    method: 'POST',
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || `Failed to start batch: ${res.statusText}`);
  }
  return res.json();
}

export async function cancelBatch(batchId: string): Promise<BatchResponse> {
  const res = await fetch(`${API_BASE}/upscale/batches/${batchId}/cancel`, {
    method: 'POST',
  });
  if (!res.ok) {
    throw new Error(`Failed to cancel batch: ${res.statusText}`);
  }
  return res.json();
}

export async function fetchBatch(batchId: string): Promise<BatchResponse> {
  const res = await fetch(`${API_BASE}/upscale/batches/${batchId}`);
  if (!res.ok) {
    throw new Error(`Failed to fetch batch: ${res.statusText}`);
  }
  return res.json();
}

export async function fetchBatchImages(batchId: string): Promise<ImageResponse[]> {
  const res = await fetch(`${API_BASE}/upscale/batches/${batchId}/images`);
  if (!res.ok) {
    throw new Error(`Failed to fetch batch images: ${res.statusText}`);
  }
  return res.json();
}

export async function retryImage(imageId: string): Promise<ImageResponse> {
  const res = await fetch(`${API_BASE}/upscale/images/${imageId}/retry`, {
    method: 'POST',
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || `Failed to retry image: ${res.statusText}`);
  }
  return res.json();
}

export function getImageInputUrl(imageId: string): string {
  return `${API_BASE}/upscale/images/${imageId}/input`;
}

export function getImageResultUrl(imageId: string): string {
  return `${API_BASE}/upscale/images/${imageId}/result`;
}

export function getBatchZipDownloadUrl(batchId: string): string {
  return `${API_BASE}/upscale/batches/${batchId}/results.zip`;
}
