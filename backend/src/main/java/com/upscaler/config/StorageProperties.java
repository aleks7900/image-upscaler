package com.upscaler.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
@ConfigurationProperties(prefix = "app.storage")
@Getter
@Setter
public class StorageProperties {
    private String root = "/data";
    private String uploadsDir = "/data/uploads";
    private String resultsDir = "/data/results";
    private String tempDir = "/data/temp";

    public Path getUploadsPath() {
        return Paths.get(uploadsDir);
    }

    public Path getResultsPath() {
        return Paths.get(resultsDir);
    }

    public Path getTempPath() {
        return Paths.get(tempDir);
    }
}
