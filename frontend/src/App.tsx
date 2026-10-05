import React, { useEffect, useState } from 'react';
import { Header } from './components/Header';
import { DropZone } from './components/DropZone';
import { BatchConfigPanel } from './components/BatchConfigPanel';
import { BatchProcessingView } from './components/BatchProcessingView';
import { ResultsGallery } from './components/ResultsGallery';
import { ImageDetailModal } from './components/ImageDetailModal';
import {
  BatchCreateRequest,
  BatchResponse,
  GpuStatusResponse,
  ImageResponse,
  ProgressEventDto,
  UploadFileItem,
} from './types';
import {
  cancelBatch,
  createBatch,
  fetchBatch,
  fetchBatchImages,
  fetchGpuStatus,
  retryImage,
  startBatch,
  uploadBatchImages,
} from './api/client';
import { subscribeToBatchEvents } from './api/sse';
import { AlertCircle } from 'lucide-react';

export const App: React.FC = () => {
  const [gpuStatus, setGpuStatus] = useState<GpuStatusResponse | null>(null);
  const [stagedFiles, setStagedFiles] = useState<UploadFileItem[]>([]);
  const [activeBatch, setActiveBatch] = useState<BatchResponse | null>(null);
  const [batchImages, setBatchImages] = useState<ImageResponse[]>([]);
  const [progress, setProgress] = useState<ProgressEventDto | null>(null);
  const [selectedImage, setSelectedImage] = useState<ImageResponse | null>(null);

  const [isStarting, setIsStarting] = useState(false);
  const [isCancelling, setIsCancelling] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const [batchConfig, setBatchConfig] = useState<BatchCreateRequest>({
    preset: 'ADOBE_STOCK',
    scale: 4,
    model: 'general',
    outputFormat: 'JPEG',
    quality: 95,
  });

  // Load GPU status
  const loadGpu = async () => {
    try {
      const status = await fetchGpuStatus();
      setGpuStatus(status);
    } catch (e) {
      console.warn('Could not query GPU status', e);
    }
  };

  useEffect(() => {
    loadGpu();
    const interval = setInterval(loadGpu, 8000);
    return () => clearInterval(interval);
  }, []);

  // Subscribe to SSE when an active batch is running
  useEffect(() => {
    if (!activeBatch || activeBatch.status === 'COMPLETED' || activeBatch.status === 'CANCELLED') {
      return;
    }

    const unsubscribe = subscribeToBatchEvents(
      activeBatch.id,
      (event) => {
        setProgress(event);
        // Refresh images list periodically or on event
        fetchBatchImages(activeBatch.id).then(setBatchImages).catch(console.error);
      },
      (completeEvent) => {
        setProgress(completeEvent);
        fetchBatch(activeBatch.id).then((b) => {
          setActiveBatch(b);
          fetchBatchImages(b.id).then(setBatchImages);
        });
      },
      (err) => console.warn('SSE error:', err)
    );

    return () => unsubscribe();
  }, [activeBatch?.id]);

  const handleStartBatch = async () => {
    if (stagedFiles.length === 0) return;
    setIsStarting(true);
    setErrorMessage(null);

    try {
      // 1. Create batch
      const batch = await createBatch(batchConfig);
      setActiveBatch(batch);

      // 2. Upload images
      const rawFiles = stagedFiles.map((s) => s.file);
      const uploaded = await uploadBatchImages(batch.id, rawFiles);
      setBatchImages(uploaded);

      // 3. Start batch processing
      const started = await startBatch(batch.id);
      setActiveBatch(started);

      // Clear staged files
      setStagedFiles([]);
    } catch (e: any) {
      setErrorMessage(e.message || 'Failed to start batch processing');
    } finally {
      setIsStarting(false);
    }
  };

  const handleCancelBatch = async () => {
    if (!activeBatch) return;
    setIsCancelling(true);
    try {
      const cancelled = await cancelBatch(activeBatch.id);
      setActiveBatch(cancelled);
      const imgs = await fetchBatchImages(activeBatch.id);
      setBatchImages(imgs);
    } catch (e: any) {
      setErrorMessage(e.message || 'Failed to cancel batch');
    } finally {
      setIsCancelling(false);
    }
  };

  const handleRetryImage = async (imageId: string) => {
    try {
      const retried = await retryImage(imageId);
      setBatchImages((prev) => prev.map((img) => (img.id === imageId ? retried : img)));
      if (activeBatch) {
        fetchBatch(activeBatch.id).then(setActiveBatch);
      }
    } catch (e: any) {
      setErrorMessage(e.message || 'Failed to retry image');
    }
  };

  return (
    <div className="min-h-screen flex flex-col bg-[#080c14] text-slate-100">
      {/* Header with GPU metrics */}
      <Header gpuStatus={gpuStatus} onRefreshGpu={loadGpu} />

      {/* Main Container */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
        {/* Error Alert */}
        {errorMessage && (
          <div className="p-4 rounded-2xl bg-rose-500/10 border border-rose-500/30 text-rose-300 text-sm flex items-center justify-between shadow-lg">
            <div className="flex items-center gap-3">
              <AlertCircle className="w-5 h-5 text-rose-400 shrink-0" />
              <span>{errorMessage}</span>
            </div>
            <button
              type="button"
              onClick={() => setErrorMessage(null)}
              className="text-xs font-semibold px-2 py-1 rounded bg-rose-500/20 hover:bg-rose-500/30 text-rose-200"
            >
              Dismiss
            </button>
          </div>
        )}

        {/* Live Processing View (if a batch is active or running) */}
        {activeBatch && (activeBatch.status === 'PROCESSING' || activeBatch.status === 'QUEUED') && (
          <BatchProcessingView
            batch={activeBatch}
            progress={progress}
            onCancel={handleCancelBatch}
            isCancelling={isCancelling}
          />
        )}

        {/* Results Gallery (if batch has processed images or is finished) */}
        {activeBatch && batchImages.length > 0 && (
          <ResultsGallery
            batch={activeBatch}
            images={batchImages}
            onRetryImage={handleRetryImage}
            onSelectImage={setSelectedImage}
          />
        )}

        {/* DropZone & Upload Workspace */}
        <section className="space-y-6">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-xl font-bold text-white tracking-tight flex items-center gap-2">
                <span>Batch Image Upload</span>
                <span className="text-xs font-mono font-medium px-2 py-0.5 rounded-full bg-cyan-500/10 text-cyan-300 border border-cyan-500/20">
                  Step 1
                </span>
              </h2>
              <p className="text-xs text-slate-400 mt-1">
                Upload up to 1,000 images (JPEG, PNG, WEBP). Staged assets are analyzed for Adobe Stock compliance.
              </p>
            </div>
          </div>

          <DropZone
            stagedFiles={stagedFiles}
            onAddFiles={(newItems) => setStagedFiles((prev) => [...prev, ...newItems])}
            onRemoveFile={(id) => setStagedFiles((prev) => prev.filter((item) => item.id !== id))}
            onClearAll={() => setStagedFiles([])}
            disabled={isStarting}
          />

          <BatchConfigPanel
            config={batchConfig}
            onChange={setBatchConfig}
            onStart={handleStartBatch}
            fileCount={stagedFiles.length}
            isStarting={isStarting}
            disabled={stagedFiles.length === 0}
          />
        </section>
      </main>

      {/* Footer */}
      <footer className="border-t border-white/5 py-6 text-center text-xs text-slate-500 font-mono">
        CUDA Batch Image Upscaler • Production Super-Resolution Pipeline • NVIDIA Blackwell/Ada/Ampere GPU
      </footer>

      {/* Comparison & Detail Modal */}
      <ImageDetailModal
        image={selectedImage}
        onClose={() => setSelectedImage(null)}
        onRetry={handleRetryImage}
      />
    </div>
  );
};

export default App;
