package com.upscaler.controller;

import com.upscaler.dto.ImageResponse;
import com.upscaler.entity.UpscaleImage;
import com.upscaler.repository.UpscaleImageRepository;
import com.upscaler.service.BatchOrchestrationService;
import com.upscaler.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/upscale/images")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class ImageController {

    private final BatchOrchestrationService orchestrationService;
    private final UpscaleImageRepository imageRepository;
    private final StorageService storageService;

    @GetMapping("/{imageId}")
    public ResponseEntity<ImageResponse> getImage(@PathVariable UUID imageId) {
        ImageResponse response = orchestrationService.getImage(imageId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{imageId}/retry")
    public ResponseEntity<ImageResponse> retryImage(@PathVariable UUID imageId) {
        ImageResponse response = orchestrationService.retryImage(imageId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{imageId}/input")
    public ResponseEntity<Resource> getInputImage(@PathVariable UUID imageId) throws IOException {
        UpscaleImage img = imageRepository.findById(imageId)
                .orElseThrow(() -> new IllegalArgumentException("Image not found: " + imageId));

        Resource resource = storageService.loadAsResource(img.getInputPath());
        Path path = Paths.get(img.getInputPath());
        String contentType = Files.probeContentType(path);
        if (contentType == null) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + img.getOriginalFilename() + "\"")
                .body(resource);
    }

    @GetMapping("/{imageId}/result")
    public ResponseEntity<Resource> getResultImage(@PathVariable UUID imageId) throws IOException {
        UpscaleImage img = imageRepository.findById(imageId)
                .orElseThrow(() -> new IllegalArgumentException("Image not found: " + imageId));

        if (img.getOutputPath() == null) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = storageService.loadAsResource(img.getOutputPath());
        Path path = Paths.get(img.getOutputPath());
        String contentType = Files.probeContentType(path);
        if (contentType == null) {
            contentType = "image/jpeg";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"upscaled_" + img.getOriginalFilename() + "\"")
                .body(resource);
    }
}
