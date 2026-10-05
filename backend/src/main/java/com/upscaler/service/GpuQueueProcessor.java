package com.upscaler.service;

import com.upscaler.config.GpuProperties;
import com.upscaler.dto.GpuStatusResponse;
import com.upscaler.dto.ProgressEventDto;
import com.upscaler.dto.StockValidationReportDto;
import com.upscaler.entity.BatchStatus;
import com.upscaler.entity.ImageStatus;
import com.upscaler.entity.ProcessingPreset;
import com.upscaler.entity.UpscaleBatch;
import com.upscaler.entity.UpscaleImage;
import com.upscaler.repository.UpscaleBatchRepository;
import com.upscaler.repository.UpscaleImageRepository;
import com.upscaler.upscaler.UpscaleProvider;
import com.upscaler.upscaler.UpscaleRequest;
import com.upscaler.upscaler.UpscaleResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class GpuQueueProcessor {

    private final UpscaleBatchRepository batchRepository;
    private final UpscaleImageRepository imageRepository;
    private final UpscaleProvider upscaleProvider;
    private final StorageService storageService;
    private final AdobeStockPostProcessor adobeStockPostProcessor;
    private final SseProgressService sseProgressService;
    private final GpuProperties gpuProperties;
    private final MeterRegistry meterRegistry;

    private ExecutorService queueExecutor;
    private Semaphore concurrencySemaphore;
    private final ConcurrentHashMap<UUID, Instant> batchStartTimes = new ConcurrentHashMap<>();

    private Counter jobsTotalCounter;
    private Counter jobsFailedTotalCounter;
    private Counter jobsCompletedTotalCounter;
    private Timer processingTimer;

    @PostConstruct
    public void init() {
        int concurrency = Math.max(1, gpuProperties.getConcurrency());
        this.queueExecutor = Executors.newFixedThreadPool(concurrency + 2);
        this.concurrencySemaphore = new Semaphore(concurrency);

        this.jobsTotalCounter = Counter.builder("upscale.jobs.total")
                .description("Total number of upscale jobs processed")
                .register(meterRegistry);
        this.jobsCompletedTotalCounter = Counter.builder("upscale.jobs.completed")
                .description("Total number of successful upscale jobs")
                .register(meterRegistry);
        this.jobsFailedTotalCounter = Counter.builder("upscale.jobs.failed")
                .description("Total number of failed upscale jobs")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("upscale.processing.time")
                .description("Time taken to upscale images")
                .register(meterRegistry);

        log.info("Initialized GpuQueueProcessor with concurrency limit: {}", concurrency);
    }

    @PreDestroy
    public void shutdown() {
        if (queueExecutor != null) {
            queueExecutor.shutdown();
        }
    }

    public void enqueueBatch(UUID batchId) {
        queueExecutor.submit(() -> {
            try {
                processBatch(batchId);
            } catch (Throwable t) {
                log.error("Fatal uncaught exception in processBatch for {}: {}", batchId, t.getMessage(), t);
            }
        });
    }

    public void enqueueSingleImage(UUID imageId) {
        queueExecutor.submit(() -> {
            try {
                processSingleImageTask(imageId);
            } catch (Throwable t) {
                log.error("Fatal uncaught exception in processSingleImageTask for {}: {}", imageId, t.getMessage(), t);
            }
        });
    }

    private void processBatch(UUID batchId) {
        try {
            UpscaleBatch batch = batchRepository.findById(batchId).orElse(null);
            if (batch == null || batch.getStatus() == BatchStatus.CANCELLED) {
                return;
            }

            batch.setStatus(BatchStatus.PROCESSING);
            if (batch.getStartedAt() == null) {
                batch.setStartedAt(Instant.now());
            }
            batchRepository.save(batch);
            batchStartTimes.putIfAbsent(batchId, batch.getStartedAt());

            List<UpscaleImage> queuedImages = imageRepository.findByBatchIdAndStatus(batchId, ImageStatus.QUEUED);
            log.info("Starting processing for batch {} with {} queued images", batchId, queuedImages.size());

            for (UpscaleImage img : queuedImages) {
                UpscaleBatch currentBatch = batchRepository.findById(batchId).orElse(null);
                if (currentBatch == null || currentBatch.getStatus() == BatchStatus.CANCELLED) {
                    log.info("Batch {} was cancelled; stopping queue execution", batchId);
                    break;
                }
                processSingleImage(img.getId());
            }

            finalizeBatchState(batchId);
        } catch (Throwable t) {
            log.error("Fatal exception during batch {} execution: {}", batchId, t.getMessage(), t);
        }
    }

    private void processSingleImageTask(UUID imageId) {
        try {
            processSingleImage(imageId);
            UpscaleImage img = imageRepository.findById(imageId).orElse(null);
            if (img != null && img.getBatch() != null) {
                finalizeBatchState(img.getBatch().getId());
            }
        } catch (Throwable t) {
            log.error("Fatal exception during single image task {}: {}", imageId, t.getMessage(), t);
        }
    }

    private void processSingleImage(UUID imageId) {
        try {
            concurrencySemaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        try {
            executeImageInference(imageId);
        } catch (Throwable t) {
            log.error("Unhandled exception in executeImageInference for image {}: {}", imageId, t.getMessage(), t);
        } finally {
            concurrencySemaphore.release();
        }
    }

    public void executeImageInference(UUID imageId) {
        Path tempPath = null;
        Path finalPath = null;
        UpscaleImage img = null;
        UpscaleBatch batch = null;

        try {
            img = imageRepository.findById(imageId).orElse(null);
            if (img == null || img.getStatus() == ImageStatus.CANCELLED) {
                return;
            }

            batch = img.getBatch();
            if (batch == null || batch.getStatus() == BatchStatus.CANCELLED) {
                img.setStatus(ImageStatus.CANCELLED);
                imageRepository.save(img);
                return;
            }

            img.setStatus(ImageStatus.PROCESSING);
            img = imageRepository.save(img);
            jobsTotalCounter.increment();

            String outExt = batch.getOutputFormat().name().toLowerCase();
            if ("jpeg".equals(outExt)) {
                outExt = "jpg";
            }

            tempPath = storageService.getTempOutputPath(batch.getId(), img.getId(), outExt);
            finalPath = storageService.getFinalOutputPath(batch.getId(), img.getId(), outExt);

            UpscaleRequest request = UpscaleRequest.builder()
                    .imageId(img.getId())
                    .batchId(batch.getId())
                    .inputPath(img.getInputPath())
                    .outputPath(tempPath.toAbsolutePath().toString())
                    .scale(batch.getScale())
                    .model(batch.getModel())
                    .outputFormat(batch.getOutputFormat().name())
                    .quality(batch.getQuality())
                    .preset(batch.getPreset().name())
                    .tileSize(gpuProperties.getTileSize())
                    .tilePadding(gpuProperties.getTilePadding())
                    .maxRetries(gpuProperties.getMaxRetries())
                    .build();

            long startTime = System.currentTimeMillis();
            UpscaleResult result = upscaleProvider.upscale(request);
            long duration = System.currentTimeMillis() - startTime;
            processingTimer.record(duration, TimeUnit.MILLISECONDS);

            if (!result.isSuccess()) {
                handleImageFailure(img, batch, result.getError() != null ? result.getError() : "Upscaling failed");
                return;
            }

            img.setStatus(ImageStatus.POST_PROCESSING);
            img.setOutputWidth(result.getOutputWidth());
            img.setOutputHeight(result.getOutputHeight());
            img.setOutputMegapixels(result.getOutputMegapixels());
            img.setOutputFormat(result.getOutputFormat());
            img.setColorProfile(result.getColorProfile());
            img.setProcessingTimeMs(duration);

            img.setStatus(ImageStatus.VALIDATING_OUTPUT);

            // Atomic file finalization: move temp to final output
            storageService.atomicMoveOutput(tempPath, finalPath);
            img.setOutputPath(finalPath.toAbsolutePath().toString());
            img.setOutputSize(Files.size(finalPath));

            // Validate Adobe Stock requirements
            StockValidationReportDto stockReport = adobeStockPostProcessor.evaluateStockReadiness(img);
            img.setStockReady(stockReport.isStockReady());

            if (batch.getPreset() == ProcessingPreset.ADOBE_STOCK && !stockReport.isStockReady()) {
                img.setStatus(ImageStatus.STOCK_VALIDATION_FAILED);
                img.setError(buildValidationFailureSummary(stockReport));
                batch.setFailedImages(batch.getFailedImages() + 1);
                jobsFailedTotalCounter.increment();
            } else {
                img.setStatus(ImageStatus.COMPLETED);
                img.setError(null);
                batch.setCompletedImages(batch.getCompletedImages() + 1);
                jobsCompletedTotalCounter.increment();
            }

            imageRepository.save(img);
            batchRepository.save(batch);

            emitProgress(batch, img);

        } catch (Throwable e) {
            log.error("Exception processing image {}: {}", imageId, e.getMessage(), e);
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException ignored) {}
            }
            handleImageFailure(img, batch, "Processing error: " + e.getMessage());
        }
    }

    private void handleImageFailure(UpscaleImage img, UpscaleBatch batch, String errorMessage) {
        if (img != null) {
            img.setStatus(ImageStatus.FAILED);
            img.setError(errorMessage);
            imageRepository.save(img);
        }

        if (batch != null) {
            batch.setFailedImages(batch.getFailedImages() + 1);
            batchRepository.save(batch);
        }
        jobsFailedTotalCounter.increment();

        if (batch != null) {
            emitProgress(batch, img);
        }
    }

    private String buildValidationFailureSummary(StockValidationReportDto report) {
        StringBuilder sb = new StringBuilder("Stock Validation Failed: ");
        if (!report.isResolutionValid()) sb.append(report.getResolutionMessage()).append("; ");
        if (!report.isMegapixelsValid()) sb.append(report.getMegapixelsMessage()).append("; ");
        if (!report.isFormatValid()) sb.append(report.getFormatMessage()).append("; ");
        if (!report.isColorProfileValid()) sb.append(report.getColorProfileMessage()).append("; ");
        if (!report.isFileSizeValid()) sb.append(report.getFileSizeMessage()).append("; ");
        return sb.toString().trim();
    }

    public void emitProgress(UpscaleBatch batch, UpscaleImage currentImage) {
        int total = batch.getTotalImages();
        int completed = batch.getCompletedImages();
        int failed = batch.getFailedImages();
        int processed = completed + failed;

        double progressPercent = total > 0 ? ((double) processed / total) * 100.0 : 0.0;

        Instant startedAt = batchStartTimes.getOrDefault(batch.getId(), batch.getStartedAt() != null ? batch.getStartedAt() : Instant.now());
        long elapsedSeconds = Math.max(0, Duration.between(startedAt, Instant.now()).getSeconds());

        Double throughput = elapsedSeconds > 0 && processed > 0
                ? (double) Math.round(((double) processed / elapsedSeconds) * 10.0) / 10.0
                : null;

        Long etaSeconds = null;
        if (throughput != null && throughput > 0 && total > processed) {
            etaSeconds = Math.round((total - processed) / throughput);
        }

        GpuStatusResponse gpuStatus = upscaleProvider.getGpuStatus();

        ProgressEventDto progressEvent = ProgressEventDto.builder()
                .batchId(batch.getId())
                .batchStatus(batch.getStatus())
                .totalImages(total)
                .completedImages(completed)
                .failedImages(failed)
                .stockReadyImages(completed)
                .progressPercent(Math.round(progressPercent * 10.0) / 10.0)
                .currentImageId(currentImage != null ? currentImage.getId() : null)
                .currentImageFilename(currentImage != null ? currentImage.getOriginalFilename() : null)
                .currentImageStatus(currentImage != null ? currentImage.getStatus() : null)
                .throughput(throughput)
                .elapsedSeconds(elapsedSeconds)
                .estimatedRemainingSeconds(etaSeconds)
                .gpuDevice(gpuStatus.getDevice())
                .gpuVramFreeMb(gpuStatus.getFreeVramMb())
                .timestamp(Instant.now())
                .build();

        sseProgressService.broadcastProgress(batch.getId(), progressEvent);
    }

    @Transactional
    public void finalizeBatchState(UUID batchId) {
        UpscaleBatch batch = batchRepository.findById(batchId).orElse(null);
        if (batch == null || batch.getStatus() == BatchStatus.CANCELLED) {
            return;
        }

        long queuedCount = imageRepository.countByBatchIdAndStatus(batchId, ImageStatus.QUEUED);
        long processingCount = imageRepository.countByBatchIdAndStatus(batchId, ImageStatus.PROCESSING);

        if (queuedCount == 0 && processingCount == 0) {
            batch.setCompletedAt(Instant.now());
            if (batch.getFailedImages() == 0 && batch.getCompletedImages() > 0) {
                batch.setStatus(BatchStatus.COMPLETED);
            } else if (batch.getCompletedImages() > 0 && batch.getFailedImages() > 0) {
                batch.setStatus(BatchStatus.PARTIALLY_COMPLETED);
            } else {
                batch.setStatus(BatchStatus.FAILED);
            }
            batchRepository.save(batch);

            storageService.cleanupBatchTemp(batchId);
            batchStartTimes.remove(batchId);

            emitProgress(batch, null);
            log.info("Batch {} processing finalized with status {}", batchId, batch.getStatus());
        }
    }
}
