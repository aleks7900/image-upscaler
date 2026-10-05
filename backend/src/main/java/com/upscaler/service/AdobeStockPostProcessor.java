package com.upscaler.service;

import com.upscaler.config.AdobeStockProperties;
import com.upscaler.dto.StockValidationReportDto;
import com.upscaler.entity.UpscaleImage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdobeStockPostProcessor {

    private final AdobeStockProperties adobeStockProperties;

    public StockValidationReportDto evaluateStockReadiness(UpscaleImage image) {
        boolean resolutionValid = image.getOutputWidth() != null && image.getOutputHeight() != null
                && image.getOutputWidth() > 0 && image.getOutputHeight() > 0;
        String resolutionMsg = resolutionValid
                ? String.format("Resolution %d × %d", image.getOutputWidth(), image.getOutputHeight())
                : "Resolution is missing or invalid";

        BigDecimal mp = image.getOutputMegapixels();
        boolean mpValid = mp != null
                && mp.compareTo(adobeStockProperties.getMinMegapixels()) >= 0
                && mp.compareTo(adobeStockProperties.getMaxMegapixels()) <= 0;
        String mpMsg = mp != null
                ? String.format("%.2f MP (Required: %.1f - %.1f MP)", mp,
                    adobeStockProperties.getMinMegapixels(), adobeStockProperties.getMaxMegapixels())
                : "Megapixel calculation missing";

        boolean formatValid = "JPEG".equalsIgnoreCase(image.getOutputFormat()) || "JPG".equalsIgnoreCase(image.getOutputFormat());
        String formatMsg = formatValid
                ? "Format: JPEG"
                : String.format("Invalid format '%s' (Adobe Stock requires JPEG)", image.getOutputFormat());

        boolean colorProfileValid = image.getColorProfile() != null && image.getColorProfile().toLowerCase().contains("srgb");
        String colorProfileMsg = colorProfileValid
                ? "Color Profile: sRGB"
                : String.format("Color profile is '%s' (Adobe Stock requires sRGB)", image.getColorProfile());

        Long sizeBytes = image.getOutputSize();
        boolean sizeValid = sizeBytes != null && sizeBytes <= adobeStockProperties.getMaxFileSizeBytes();
        String sizeMsg = sizeBytes != null
                ? String.format("%.2f MB (Limit: %d MB)", (double) sizeBytes / (1024 * 1024),
                    adobeStockProperties.getMaxFileSizeBytes() / (1024 * 1024))
                : "File size unknown";

        boolean allValid = resolutionValid && mpValid && formatValid && colorProfileValid && sizeValid;

        return StockValidationReportDto.builder()
                .resolutionValid(resolutionValid)
                .resolutionMessage(resolutionMsg)
                .megapixelsValid(mpValid)
                .megapixelsMessage(mpMsg)
                .formatValid(formatValid)
                .formatMessage(formatMsg)
                .colorProfileValid(colorProfileValid)
                .colorProfileMessage(colorProfileMsg)
                .fileSizeValid(sizeValid)
                .fileSizeMessage(sizeMsg)
                .stockReady(allValid)
                .build();
    }
}
