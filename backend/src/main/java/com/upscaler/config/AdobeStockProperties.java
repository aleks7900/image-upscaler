package com.upscaler.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
@ConfigurationProperties(prefix = "app.adobe-stock")
@Getter
@Setter
public class AdobeStockProperties {
    private BigDecimal minMegapixels = new BigDecimal("4.00");
    private BigDecimal maxMegapixels = new BigDecimal("100.00");
    private long maxFileSizeBytes = 45L * 1024 * 1024; // 45 MB
    private int defaultQuality = 95;
    private int minQuality = 85;
    private String outputFormat = "JPEG";
    private String colorProfile = "sRGB";
}
