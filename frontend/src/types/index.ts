export type BatchStatus =
  | 'CREATED'
  | 'UPLOADING'
  | 'QUEUED'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'PARTIALLY_COMPLETED'
  | 'FAILED'
  | 'CANCELLED';

export type ImageStatus =
  | 'UPLOADED'
  | 'VALIDATING'
  | 'QUEUED'
  | 'PROCESSING'
  | 'POST_PROCESSING'
  | 'VALIDATING_OUTPUT'
  | 'COMPLETED'
  | 'STOCK_VALIDATION_FAILED'
  | 'FAILED'
  | 'CANCELLED';

export type ProcessingPreset = 'ADOBE_STOCK' | 'CUSTOM';
export type OutputFormat = 'JPEG' | 'PNG' | 'WEBP';

export interface ImageResponse {
  id: string;
  batchId: string;
  originalFilename: string;
  status: ImageStatus;
  outputPath?: string | null;
  inputWidth: number | null;
  inputHeight: number | null;
  inputMegapixels: number | null;
  inputSize: number | null;
  outputWidth: number | null;
  outputHeight: number | null;
  outputMegapixels: number | null;
  outputSize: number | null;
  outputFormat: string | null;
  colorProfile: string | null;
  processingTimeMs: number | null;
  stockReady: boolean;
  error: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface BatchResponse {
  id: string;
  status: BatchStatus;
  preset: ProcessingPreset;
  scale: number;
  model: string;
  outputFormat: OutputFormat;
  quality: number;
  totalImages: number;
  completedImages: number;
  failedImages: number;
  stockReadyImages: number;
  createdAt: string;
  startedAt: string | null;
  completedAt: string | null;
  error: string | null;
  images?: ImageResponse[];
}

export interface BatchCreateRequest {
  preset: ProcessingPreset;
  scale: number;
  model: string;
  outputFormat: OutputFormat;
  quality: number;
}

export interface GpuStatusResponse {
  available: boolean;
  workerReady: boolean;
  device: string;
  cudaAvailable: boolean;
  cudaVersion: string;
  pytorchVersion: string;
  totalVramMb: number;
  freeVramMb: number;
  modelLoaded: boolean;
  loadedModel: string;
  activeJobs: number;
  queuedJobs: number;
}

export interface ProgressEventDto {
  batchId: string;
  batchStatus: BatchStatus;
  totalImages: number;
  completedImages: number;
  failedImages: number;
  stockReadyImages: number;
  progressPercent: number;
  currentImageId: string | null;
  currentImageFilename: string | null;
  currentImageStatus: ImageStatus | null;
  throughput: number | null;
  elapsedSeconds: number | null;
  estimatedRemainingSeconds: number | null;
  gpuDevice: string | null;
  gpuVramFreeMb: number | null;
  timestamp: string;
}

export interface UploadFileItem {
  id: string;
  file: File;
  previewUrl: string;
  width: number | null;
  height: number | null;
  megapixels: number | null;
  size: number;
  format: string;
  status: 'PENDING' | 'UPLOADING' | 'UPLOADED' | 'ERROR';
  error?: string;
}
