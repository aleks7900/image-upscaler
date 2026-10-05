package com.upscaler.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.gpu")
@Getter
@Setter
public class GpuProperties {
    private String workerUrl = "http://gpu-worker:8000";
    private int concurrency = 1;
    private int timeoutSeconds = 300;
    private int tileSize = 512;
    private int tilePadding = 32;
    private int maxRetries = 3;
}
