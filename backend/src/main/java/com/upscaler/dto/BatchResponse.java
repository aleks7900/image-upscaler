package com.upscaler.dto;

import com.upscaler.entity.BatchStatus;
import com.upscaler.entity.OutputFormat;
import com.upscaler.entity.ProcessingPreset;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchResponse {
    private UUID id;
    private BatchStatus status;
    private ProcessingPreset preset;
    private Integer scale;
    private String model;
    private OutputFormat outputFormat;
    private Integer quality;
    private Integer totalImages;
    private Integer completedImages;
    private Integer failedImages;
    private Integer stockReadyImages;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    private String error;
    private List<ImageResponse> images;
}
