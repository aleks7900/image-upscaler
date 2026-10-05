package com.upscaler;

import com.upscaler.entity.UpscaleImage;
import com.upscaler.service.AdobeStockPostProcessor;
import com.upscaler.dto.StockValidationReportDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "app.upscale.provider=mock")
@ActiveProfiles("test")
class AdobeStockValidationTest {

    @Autowired
    private AdobeStockPostProcessor stockPostProcessor;

    @Test
    void testStockReadyWhenAllConditionsMet() {
        UpscaleImage image = UpscaleImage.builder()
                .id(UUID.randomUUID())
                .outputWidth(4096)
                .outputHeight(4096)
                .outputMegapixels(new BigDecimal("16.78"))
                .outputFormat("JPEG")
                .colorProfile("sRGB IEC61966-2.1")
                .outputSize(8_400_000L) // 8.4 MB
                .build();

        StockValidationReportDto report = stockPostProcessor.evaluateStockReadiness(image);
        assertTrue(report.isStockReady(), "Image meeting all criteria must be stock ready");
        assertTrue(report.isResolutionValid());
        assertTrue(report.isMegapixelsValid());
        assertTrue(report.isFormatValid());
        assertTrue(report.isColorProfileValid());
        assertTrue(report.isFileSizeValid());
    }

    @Test
    void testFailsWhenMegapixelsBelowMinimum4MP() {
        UpscaleImage image = UpscaleImage.builder()
                .id(UUID.randomUUID())
                .outputWidth(1000)
                .outputHeight(1000)
                .outputMegapixels(new BigDecimal("1.00")) // < 4.0 MP
                .outputFormat("JPEG")
                .colorProfile("sRGB")
                .outputSize(1_000_000L)
                .build();

        StockValidationReportDto report = stockPostProcessor.evaluateStockReadiness(image);
        assertFalse(report.isStockReady());
        assertFalse(report.isMegapixelsValid(), "Megapixels under 4.0 MP must fail validation");
    }

    @Test
    void testFailsWhenMegapixelsExceedsMaximum100MP() {
        UpscaleImage image = UpscaleImage.builder()
                .id(UUID.randomUUID())
                .outputWidth(12000)
                .outputHeight(10000)
                .outputMegapixels(new BigDecimal("120.00")) // > 100 MP
                .outputFormat("JPEG")
                .colorProfile("sRGB")
                .outputSize(15_000_000L)
                .build();

        StockValidationReportDto report = stockPostProcessor.evaluateStockReadiness(image);
        assertFalse(report.isStockReady());
        assertFalse(report.isMegapixelsValid(), "Megapixels above 100.0 MP must fail validation");
    }

    @Test
    void testFailsWhenFileSizeExceeds45MB() {
        UpscaleImage image = UpscaleImage.builder()
                .id(UUID.randomUUID())
                .outputWidth(5000)
                .outputHeight(4000)
                .outputMegapixels(new BigDecimal("20.00"))
                .outputFormat("JPEG")
                .colorProfile("sRGB")
                .outputSize(50L * 1024 * 1024) // 50 MB > 45 MB
                .build();

        StockValidationReportDto report = stockPostProcessor.evaluateStockReadiness(image);
        assertFalse(report.isStockReady());
        assertFalse(report.isFileSizeValid(), "File size exceeding 45 MB must fail validation");
    }

    @Test
    void testFailsWhenFormatIsNotJpeg() {
        UpscaleImage image = UpscaleImage.builder()
                .id(UUID.randomUUID())
                .outputWidth(4000)
                .outputHeight(4000)
                .outputMegapixels(new BigDecimal("16.00"))
                .outputFormat("PNG") // Not JPEG
                .colorProfile("sRGB")
                .outputSize(10_000_000L)
                .build();

        StockValidationReportDto report = stockPostProcessor.evaluateStockReadiness(image);
        assertFalse(report.isStockReady());
        assertFalse(report.isFormatValid(), "Non-JPEG output format must fail Adobe Stock validation");
    }
}
