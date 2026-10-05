package com.upscaler.service;

import com.upscaler.config.UpscaleProperties;
import com.upscaler.dto.BatchCreateRequest;
import com.upscaler.dto.BatchResponse;
import com.upscaler.dto.ImageResponse;
import com.upscaler.entity.*;
import com.upscaler.repository.UpscaleBatchRepository;
import com.upscaler.repository.UpscaleImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchOrchestrationService {

    private final UpscaleBatchRepository batchRepository;
    private final UpscaleImageRepository imageRepository;
    private final StorageService storageService;
    private final ImageValidationService validationService;
    private final GpuQueueProcessor queueProcessor;
    private final UpscaleProperties upscaleProperties;

    @Transactional
    public BatchResponse createBatch(BatchCreateRequest request) {
        UpscaleBatch batch = UpscaleBatch.builder()
                .id(UUID.randomUUID())
                .status(BatchStatus.CREATED)
                .preset(request.getPreset() != null ? request.getPreset() : ProcessingPreset.ADOBE_STOCK)
                .scale(request.getScale() != null ? request.getScale() : upscaleProperties.getDefaultScale())
                .model(request.getModel() != null ? request.getModel() : upscaleProperties.getDefaultModel())
                .outputFormat(request.getOutputFormat() != null ? request.getOutputFormat() : OutputFormat.JPEG)
                .quality(request.getQuality() != null ? request.getQuality() : 95)
                .totalImages(0)
                .completedImages(0)
                .failedImages(0)
                .createdAt(Instant.now())
                .build();

        batch = batchRepository.save(batch);
        log.info("Created upscale batch: {} (preset: {}, scale: {}x, model: {})",
                batch.getId(), batch.getPreset(), batch.getScale(), batch.getModel());
        return mapToBatchResponse(batch, false);
    }

    @Transactional
    public List<ImageResponse> uploadImages(UUID batchId, List<MultipartFile> files) {
        UpscaleBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));

        if (batch.getStatus() != BatchStatus.CREATED && batch.getStatus() != BatchStatus.UPLOADING) {
            throw new IllegalStateException("Cannot add images to batch with status: " + batch.getStatus());
        }

        if (batch.getTotalImages() + files.size() > upscaleProperties.getMaxBatchImages()) {
            throw new IllegalArgumentException(String.format("Batch image count (%d) would exceed maximum limit of %d images",
                    batch.getTotalImages() + files.size(), upscaleProperties.getMaxBatchImages()));
        }

        batch.setStatus(BatchStatus.UPLOADING);
        List<ImageResponse> responseList = new ArrayList<>();

        for (MultipartFile file : files) {
            // 1. Validate payload magic bytes & size
            validationService.validateUploadPayload(file);

            UUID imageId = UUID.randomUUID();
            Path storedPath;
            try {
                storedPath = storageService.storeUpload(batchId, imageId, file);
            } catch (IOException e) {
                log.error("Failed to store uploaded file {}: {}", file.getOriginalFilename(), e.getMessage());
                throw new RuntimeException("Failed to store file: " + file.getOriginalFilename(), e);
            }

            // 2. Validate image dimensions and header sanity
            ImageValidationService.ImageDimensions dims = validationService.inspectAndValidateStoredImage(storedPath);

            long fileSize;
            try {
                fileSize = Files.size(storedPath);
            } catch (IOException e) {
                fileSize = file.getSize();
            }

            UpscaleImage img = UpscaleImage.builder()
                    .id(imageId)
                    .batch(batch)
                    .originalFilename(file.getOriginalFilename())
                    .inputPath(storedPath.toAbsolutePath().toString())
                    .status(ImageStatus.QUEUED)
                    .inputWidth(dims.getWidth())
                    .inputHeight(dims.getHeight())
                    .inputMegapixels(dims.getMegapixels())
                    .inputSize(fileSize)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            img = imageRepository.save(img);
            responseList.add(mapToImageResponse(img));
        }

        batch.setTotalImages(batch.getTotalImages() + files.size());
        batchRepository.save(batch);

        log.info("Uploaded {} images to batch {}", files.size(), batchId);
        return responseList;
    }

    @Transactional
    public BatchResponse startBatch(UUID batchId) {
        UpscaleBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));

        if (batch.getStatus() == BatchStatus.PROCESSING || batch.getStatus() == BatchStatus.COMPLETED) {
            return mapToBatchResponse(batch, true);
        }

        if (batch.getTotalImages() == 0) {
            throw new IllegalStateException("Cannot start empty batch: " + batchId);
        }

        batch.setStatus(BatchStatus.QUEUED);
        batch.setStartedAt(Instant.now());
        batch = batchRepository.save(batch);

        queueProcessor.enqueueBatch(batchId);
        log.info("Started batch processing for batch {}", batchId);
        return mapToBatchResponse(batch, true);
    }

    @Transactional
    public BatchResponse cancelBatch(UUID batchId) {
        UpscaleBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));

        batch.setStatus(BatchStatus.CANCELLED);
        batch = batchRepository.save(batch);

        List<UpscaleImage> images = imageRepository.findByBatchIdOrderByCreatedAtAsc(batchId);
        for (UpscaleImage img : images) {
            if (img.getStatus() == ImageStatus.QUEUED || img.getStatus() == ImageStatus.UPLOADED) {
                img.setStatus(ImageStatus.CANCELLED);
                imageRepository.save(img);
            }
        }

        storageService.cleanupBatchTemp(batchId);
        log.info("Cancelled batch {}", batchId);
        return mapToBatchResponse(batch, true);
    }

    @Transactional
    public ImageResponse retryImage(UUID imageId) {
        UpscaleImage img = imageRepository.findById(imageId)
                .orElseThrow(() -> new IllegalArgumentException("Image not found: " + imageId));

        UpscaleBatch batch = img.getBatch();
        if (img.getStatus() == ImageStatus.COMPLETED) {
            return mapToImageResponse(img);
        }

        if (img.getStatus() == ImageStatus.FAILED || img.getStatus() == ImageStatus.STOCK_VALIDATION_FAILED) {
            batch.setFailedImages(Math.max(0, batch.getFailedImages() - 1));
        }

        img.setStatus(ImageStatus.QUEUED);
        img.setError(null);
        img = imageRepository.save(img);

        if (batch.getStatus() == BatchStatus.COMPLETED || batch.getStatus() == BatchStatus.PARTIALLY_COMPLETED || batch.getStatus() == BatchStatus.FAILED) {
            batch.setStatus(BatchStatus.PROCESSING);
        }
        batchRepository.save(batch);

        queueProcessor.enqueueSingleImage(imageId);
        log.info("Retrying image {} in batch {}", imageId, batch.getId());
        return mapToImageResponse(img);
    }

    @Transactional(readOnly = true)
    public BatchResponse getBatch(UUID batchId) {
        UpscaleBatch batch = batchRepository.findByIdWithImages(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));
        return mapToBatchResponse(batch, true);
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> listBatches() {
        return batchRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(b -> mapToBatchResponse(b, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ImageResponse> getBatchImages(UUID batchId) {
        return imageRepository.findByBatchIdOrderByCreatedAtAsc(batchId)
                .stream()
                .map(this::mapToImageResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ImageResponse getImage(UUID imageId) {
        UpscaleImage img = imageRepository.findById(imageId)
                .orElseThrow(() -> new IllegalArgumentException("Image not found: " + imageId));
        return mapToImageResponse(img);
    }

    private BatchResponse mapToBatchResponse(UpscaleBatch batch, boolean includeImages) {
        List<ImageResponse> imageResponses = null;
        if (includeImages) {
            imageResponses = imageRepository.findByBatchIdOrderByCreatedAtAsc(batch.getId())
                    .stream()
                    .map(this::mapToImageResponse)
                    .toList();
        }

        return BatchResponse.builder()
                .id(batch.getId())
                .status(batch.getStatus())
                .preset(batch.getPreset())
                .scale(batch.getScale())
                .model(batch.getModel())
                .outputFormat(batch.getOutputFormat())
                .quality(batch.getQuality())
                .totalImages(batch.getTotalImages())
                .completedImages(batch.getCompletedImages())
                .failedImages(batch.getFailedImages())
                .stockReadyImages(batch.getCompletedImages())
                .createdAt(batch.getCreatedAt())
                .startedAt(batch.getStartedAt())
                .completedAt(batch.getCompletedAt())
                .error(batch.getError())
                .images(imageResponses)
                .build();
    }

    public ImageResponse mapToImageResponse(UpscaleImage img) {
        return ImageResponse.builder()
                .id(img.getId())
                .batchId(img.getBatch().getId())
                .originalFilename(img.getOriginalFilename())
                .status(img.getStatus())
                .inputWidth(img.getInputWidth())
                .inputHeight(img.getInputHeight())
                .inputMegapixels(img.getInputMegapixels())
                .inputSize(img.getInputSize())
                .outputWidth(img.getOutputWidth())
                .outputHeight(img.getOutputHeight())
                .outputMegapixels(img.getOutputMegapixels())
                .outputSize(img.getOutputSize())
                .outputFormat(img.getOutputFormat())
                .colorProfile(img.getColorProfile())
                .processingTimeMs(img.getProcessingTimeMs())
                .stockReady(img.getStockReady())
                .error(img.getError())
                .createdAt(img.getCreatedAt())
                .updatedAt(img.getUpdatedAt())
                .build();
    }
}
