package com.upscaler.upscaler;

import com.upscaler.dto.GpuStatusResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.upscale.provider", havingValue = "mock")
@Slf4j
public class MockUpscaleProvider implements UpscaleProvider {

    @Override
    public UpscaleResult upscale(UpscaleRequest request) {
        log.info("Executing mock upscale for image {} (scale: {}x)", request.getImageId(), request.getScale());
        try {
            File inputFile = new File(request.getInputPath());
            if (!inputFile.exists()) {
                return UpscaleResult.builder()
                        .success(false)
                        .error("Input file does not exist: " + request.getInputPath())
                        .build();
            }

            BufferedImage image = ImageIO.read(inputFile);
            int inW = image != null ? image.getWidth() : 1000;
            int inH = image != null ? image.getHeight() : 1000;
            int outW = inW * request.getScale();
            int outH = inH * request.getScale();

            BigDecimal mp = BigDecimal.valueOf(outW)
                    .multiply(BigDecimal.valueOf(outH))
                    .divide(BigDecimal.valueOf(1_000_000), 2, RoundingMode.HALF_UP);

            File outputFile = new File(request.getOutputPath());
            if (outputFile.getParentFile() != null) {
                outputFile.getParentFile().mkdirs();
            }

            // Create scaled mock output
            BufferedImage scaled = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_RGB);
            ImageIO.write(scaled, "jpg", outputFile);

            long size = Files.size(outputFile.toPath());
            boolean stockReady = mp.compareTo(new BigDecimal("4.00")) >= 0 &&
                    mp.compareTo(new BigDecimal("100.00")) <= 0 &&
                    size <= 45L * 1024 * 1024;

            return UpscaleResult.builder()
                    .success(true)
                    .outputWidth(outW)
                    .outputHeight(outH)
                    .outputMegapixels(mp)
                    .outputSize(size)
                    .outputFormat("JPEG")
                    .colorProfile("sRGB")
                    .processingTimeMs(45L)
                    .stockReady(stockReady)
                    .retriesUsed(0)
                    .finalTileSize(request.getTileSize())
                    .build();

        } catch (Exception e) {
            log.error("Mock upscale failed: {}", e.getMessage(), e);
            return UpscaleResult.builder()
                    .success(false)
                    .error("Mock upscale failed: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public GpuStatusResponse getGpuStatus() {
        return GpuStatusResponse.builder()
                .available(true)
                .workerReady(true)
                .device("Mock NVIDIA RTX 4090 (Test Engine)")
                .cudaAvailable(true)
                .cudaVersion("12.8")
                .pytorchVersion("2.6.0")
                .totalVramMb(24576L)
                .freeVramMb(22000L)
                .modelLoaded(true)
                .loadedModel("general")
                .activeJobs(0)
                .queuedJobs(0)
                .build();
    }

    @Override
    public List<String> getSupportedModels() {
        return List.of("general", "anime");
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
