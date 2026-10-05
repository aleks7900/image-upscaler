package com.upscaler.dto;

import com.upscaler.entity.BatchStatus;
import com.upscaler.entity.ImageStatus;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProgressEventDto {
    private UUID batchId;
    private BatchStatus batchStatus;
    private int totalImages;
    private int completedImages;
    private int failedImages;
    private int stockReadyImages;
    private double progressPercent;
    private UUID currentImageId;
    private String currentImageFilename;
    private ImageStatus currentImageStatus;
    private Double throughput;
    private Long elapsedSeconds;
    private Long estimatedRemainingSeconds;
    private String gpuDevice;
    private Long gpuVramFreeMb;
    private Instant timestamp;
}
