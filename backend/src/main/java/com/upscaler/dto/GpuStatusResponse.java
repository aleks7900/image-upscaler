package com.upscaler.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GpuStatusResponse {
    private boolean available;
    private boolean workerReady;
    private String device;
    private boolean cudaAvailable;
    private String cudaVersion;
    private String pytorchVersion;
    private Long totalVramMb;
    private Long freeVramMb;
    private boolean modelLoaded;
    private String loadedModel;
    private int activeJobs;
    private int queuedJobs;
}
