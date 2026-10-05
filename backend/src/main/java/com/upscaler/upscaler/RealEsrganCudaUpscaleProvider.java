package com.upscaler.upscaler;

import com.upscaler.config.GpuProperties;
import com.upscaler.dto.GpuStatusResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.upscale.provider", havingValue = "cuda", matchIfMissing = true)
@Slf4j
public class RealEsrganCudaUpscaleProvider implements UpscaleProvider {

    private final GpuProperties gpuProperties;
    private final RestTemplate restTemplate;

    public RealEsrganCudaUpscaleProvider(GpuProperties gpuProperties, RestTemplateBuilder restTemplateBuilder) {
        this.gpuProperties = gpuProperties;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(gpuProperties.getTimeoutSeconds()))
                .build();
    }

    @Override
    public UpscaleResult upscale(UpscaleRequest request) {
        String url = gpuProperties.getWorkerUrl() + "/upscale";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<UpscaleRequest> entity = new HttpEntity<>(request, headers);

        try {
            log.info("Sending image upscale job to GPU worker at {} (image: {}, scale: {}x, model: {})",
                    url, request.getImageId(), request.getScale(), request.getModel());
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<>() {}
            );

            Map<String, Object> body = response.getBody();
            if (body == null || !Boolean.TRUE.equals(body.get("success"))) {
                String error = body != null && body.containsKey("error") ? String.valueOf(body.get("error")) : "Unknown worker error";
                return UpscaleResult.builder()
                        .success(false)
                        .error(error)
                        .build();
            }

            Integer outWidth = body.get("output_width") != null ? ((Number) body.get("output_width")).intValue() : null;
            Integer outHeight = body.get("output_height") != null ? ((Number) body.get("output_height")).intValue() : null;
            BigDecimal outMp = body.get("output_megapixels") != null ? new BigDecimal(String.valueOf(body.get("output_megapixels"))) : null;
            Long outSize = body.get("output_size") != null ? ((Number) body.get("output_size")).longValue() : null;
            String outFormat = (String) body.get("output_format");
            String colorProfile = (String) body.get("color_profile");
            Long durationMs = body.get("processing_time_ms") != null ? ((Number) body.get("processing_time_ms")).longValue() : 0L;
            Boolean stockReady = (Boolean) body.get("stock_ready");
            Integer retriesUsed = body.get("retries_used") != null ? ((Number) body.get("retries_used")).intValue() : 0;
            Integer finalTileSize = body.get("final_tile_size") != null ? ((Number) body.get("final_tile_size")).intValue() : request.getTileSize();

            return UpscaleResult.builder()
                    .success(true)
                    .outputWidth(outWidth)
                    .outputHeight(outHeight)
                    .outputMegapixels(outMp)
                    .outputSize(outSize)
                    .outputFormat(outFormat)
                    .colorProfile(colorProfile)
                    .processingTimeMs(durationMs)
                    .stockReady(stockReady)
                    .retriesUsed(retriesUsed)
                    .finalTileSize(finalTileSize)
                    .build();

        } catch (RestClientResponseException e) {
            log.error("GPU worker returned error code {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return UpscaleResult.builder()
                    .success(false)
                    .error("GPU worker error " + e.getStatusCode() + ": " + e.getResponseBodyAsString())
                    .build();
        } catch (ResourceAccessException e) {
            log.error("Failed to connect to GPU worker at {}: {}", url, e.getMessage());
            return UpscaleResult.builder()
                    .success(false)
                    .error("GPU worker unreachable: " + e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("Unexpected error invoking GPU worker: {}", e.getMessage(), e);
            return UpscaleResult.builder()
                    .success(false)
                    .error("Unexpected upscale failure: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public GpuStatusResponse getGpuStatus() {
        String url = gpuProperties.getWorkerUrl() + "/gpu/info";
        try {
            ResponseEntity<GpuStatusResponse> response = restTemplate.getForEntity(url, GpuStatusResponse.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.debug("GPU worker not responding to status inquiry at {}: {}", url, e.getMessage());
        }

        return GpuStatusResponse.builder()
                .available(false)
                .workerReady(false)
                .device("Unavailable")
                .cudaAvailable(false)
                .cudaVersion("N/A")
                .pytorchVersion("N/A")
                .totalVramMb(0L)
                .freeVramMb(0L)
                .modelLoaded(false)
                .loadedModel("none")
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
        try {
            String url = gpuProperties.getWorkerUrl() + "/health";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }
}
