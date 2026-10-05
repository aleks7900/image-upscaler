package com.upscaler.controller;

import com.upscaler.dto.BatchCreateRequest;
import com.upscaler.dto.BatchResponse;
import com.upscaler.dto.ImageResponse;
import com.upscaler.service.BatchOrchestrationService;
import com.upscaler.service.SseProgressService;
import com.upscaler.service.ZipExportService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/upscale/batches")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class BatchController {

    private final BatchOrchestrationService orchestrationService;
    private final SseProgressService sseProgressService;
    private final ZipExportService zipExportService;

    @PostMapping
    public ResponseEntity<BatchResponse> createBatch(@Valid @RequestBody(required = false) BatchCreateRequest request) {
        if (request == null) {
            request = new BatchCreateRequest();
        }
        BatchResponse response = orchestrationService.createBatch(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(value = "/{batchId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<ImageResponse>> uploadImages(
            @PathVariable UUID batchId,
            @RequestParam("files") List<MultipartFile> files) {
        List<ImageResponse> responses = orchestrationService.uploadImages(batchId, files);
        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }

    @PostMapping("/{batchId}/start")
    public ResponseEntity<BatchResponse> startBatch(@PathVariable UUID batchId) {
        BatchResponse response = orchestrationService.startBatch(batchId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{batchId}/cancel")
    public ResponseEntity<BatchResponse> cancelBatch(@PathVariable UUID batchId) {
        BatchResponse response = orchestrationService.cancelBatch(batchId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<BatchResponse>> listBatches() {
        List<BatchResponse> responses = orchestrationService.listBatches();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{batchId}")
    public ResponseEntity<BatchResponse> getBatch(@PathVariable UUID batchId) {
        BatchResponse response = orchestrationService.getBatch(batchId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{batchId}/images")
    public ResponseEntity<List<ImageResponse>> getBatchImages(@PathVariable UUID batchId) {
        List<ImageResponse> responses = orchestrationService.getBatchImages(batchId);
        return ResponseEntity.ok(responses);
    }

    @GetMapping(value = "/{batchId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents(@PathVariable UUID batchId) {
        return sseProgressService.subscribe(batchId);
    }

    @GetMapping(value = "/{batchId}/results.zip", produces = "application/zip")
    public void downloadResultsZip(@PathVariable UUID batchId, HttpServletResponse response) throws IOException {
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"batch_" + batchId + "_results.zip\"");
        zipExportService.streamBatchResultsZip(batchId, response.getOutputStream());
    }
}
