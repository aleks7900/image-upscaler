package com.upscaler.controller;

import com.upscaler.dto.GpuStatusResponse;
import com.upscaler.upscaler.UpscaleProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class SystemController {

    private final UpscaleProvider upscaleProvider;

    @GetMapping("/gpu")
    public ResponseEntity<GpuStatusResponse> getGpuStatus() {
        GpuStatusResponse status = upscaleProvider.getGpuStatus();
        return ResponseEntity.ok(status);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        boolean workerHealthy = upscaleProvider.isAvailable();
        return ResponseEntity.ok(Map.of(
                "status", workerHealthy ? "UP" : "DEGRADED",
                "workerAvailable", workerHealthy,
                "supportedModels", upscaleProvider.getSupportedModels()
        ));
    }
}
