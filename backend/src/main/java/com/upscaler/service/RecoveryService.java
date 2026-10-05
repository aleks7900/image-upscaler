package com.upscaler.service;

import com.upscaler.entity.BatchStatus;
import com.upscaler.entity.ImageStatus;
import com.upscaler.entity.UpscaleBatch;
import com.upscaler.entity.UpscaleImage;
import com.upscaler.repository.UpscaleBatchRepository;
import com.upscaler.repository.UpscaleImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecoveryService {

    private final UpscaleBatchRepository batchRepository;
    private final UpscaleImageRepository imageRepository;
    private final GpuQueueProcessor queueProcessor;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void reconcileBatchesOnStartup() {
        log.info("Running startup batch state reconciliation...");

        List<ImageStatus> stuckStatuses = List.of(
                ImageStatus.PROCESSING,
                ImageStatus.POST_PROCESSING,
                ImageStatus.VALIDATING_OUTPUT
        );

        List<UpscaleImage> stuckImages = imageRepository.findByStatusInOrderByCreatedAtAsc(stuckStatuses);
        if (stuckImages.isEmpty()) {
            log.info("No interrupted images found. State is clean.");
        } else {
            log.warn("Found {} interrupted images from previous run. Reconciling...", stuckImages.size());
            for (UpscaleImage img : stuckImages) {
                // Check if final output already exists on disk
                if (img.getOutputPath() != null && new File(img.getOutputPath()).exists()) {
                    log.info("Image {} output exists at {}. Marking COMPLETED.", img.getId(), img.getOutputPath());
                    img.setStatus(ImageStatus.COMPLETED);
                } else {
                    log.info("Image {} output incomplete. Requeuing to QUEUED.", img.getId());
                    img.setStatus(ImageStatus.QUEUED);
                }
                imageRepository.save(img);
            }
        }

        // Reconcile and resume interrupted batches
        List<UpscaleBatch> unfinishedBatches = batchRepository.findByStatusIn(List.of(BatchStatus.PROCESSING, BatchStatus.QUEUED));
        for (UpscaleBatch batch : unfinishedBatches) {
            log.info("Resuming queued/processing batch {}", batch.getId());
            queueProcessor.enqueueBatch(batch.getId());
        }

        log.info("Startup batch reconciliation completed.");
    }
}
