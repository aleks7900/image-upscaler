package com.upscaler.upscaler;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpscaleRequest {
    private UUID imageId;
    private UUID batchId;
    private String inputPath;
    private String outputPath;
    private int scale;
    private String model;
    private String outputFormat;
    private int quality;
    private String preset;
    private int tileSize;
    private int tilePadding;
    private int maxRetries;
}
