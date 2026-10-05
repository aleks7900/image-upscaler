package com.upscaler.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.upscale")
@Getter
@Setter
public class UpscaleProperties {
    private String defaultModel = "general";
    private int defaultScale = 4;
    private long maxFileSizeBytes = 100L * 1024 * 1024; // 100 MB
    private int maxBatchImages = 1000;
    private int maxDimension = 12000;
    private long maxPixelCount = 64_000_000L;
    private int tileSize = 512;
    private int tilePadding = 32;
}
