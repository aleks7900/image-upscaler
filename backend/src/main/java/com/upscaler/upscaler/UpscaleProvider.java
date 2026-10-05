package com.upscaler.upscaler;

import com.upscaler.dto.GpuStatusResponse;

import java.util.List;

public interface UpscaleProvider {
    UpscaleResult upscale(UpscaleRequest request);

    GpuStatusResponse getGpuStatus();

    List<String> getSupportedModels();

    boolean isAvailable();
}
