package com.upscaler.service;

import com.upscaler.config.StorageProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageService {

    private final StorageProperties storageProperties;

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(storageProperties.getUploadsPath());
            Files.createDirectories(storageProperties.getResultsPath());
            Files.createDirectories(storageProperties.getTempPath());
            log.info("Initialized storage directories: root={}, uploads={}, results={}, temp={}",
                    storageProperties.getRoot(),
                    storageProperties.getUploadsPath(),
                    storageProperties.getResultsPath(),
                    storageProperties.getTempPath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize storage directories: " + e.getMessage(), e);
        }
    }

    public Path storeUpload(UUID batchId, UUID imageId, MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String extension = FilenameUtils.getExtension(originalFilename);
        if (extension == null || extension.isBlank()) {
            extension = "png";
        }
        extension = extension.toLowerCase();

        Path batchUploadDir = storageProperties.getUploadsPath().resolve(batchId.toString()).normalize();
        validatePathTraversal(storageProperties.getUploadsPath(), batchUploadDir);
        Files.createDirectories(batchUploadDir);

        Path targetPath = batchUploadDir.resolve(imageId + "." + extension).normalize();
        validatePathTraversal(batchUploadDir, targetPath);

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        log.debug("Stored uploaded file for batch {} image {} at {}", batchId, imageId, targetPath);
        return targetPath;
    }

    public Path getTempOutputPath(UUID batchId, UUID imageId, String extension) throws IOException {
        Path batchTempDir = storageProperties.getTempPath().resolve(batchId.toString()).normalize();
        validatePathTraversal(storageProperties.getTempPath(), batchTempDir);
        Files.createDirectories(batchTempDir);

        Path tempPath = batchTempDir.resolve(imageId + ".tmp." + extension.toLowerCase()).normalize();
        validatePathTraversal(batchTempDir, tempPath);
        return tempPath;
    }

    public Path getFinalOutputPath(UUID batchId, UUID imageId, String extension) throws IOException {
        Path batchResultsDir = storageProperties.getResultsPath().resolve(batchId.toString()).normalize();
        validatePathTraversal(storageProperties.getResultsPath(), batchResultsDir);
        Files.createDirectories(batchResultsDir);

        Path finalPath = batchResultsDir.resolve(imageId + "." + extension.toLowerCase()).normalize();
        validatePathTraversal(batchResultsDir, finalPath);
        return finalPath;
    }

    public void atomicMoveOutput(Path tempPath, Path finalPath) throws IOException {
        if (!Files.exists(tempPath)) {
            throw new NoSuchFileException("Temporary result file does not exist: " + tempPath);
        }
        try {
            Files.move(tempPath, finalPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            // Fallback to standard replace
            Files.move(tempPath, finalPath, StandardCopyOption.REPLACE_EXISTING);
        }
        log.debug("Atomically finalized output: {} -> {}", tempPath, finalPath);
    }

    public Resource loadAsResource(String pathString) {
        Path path = Paths.get(pathString).normalize();
        if (!Files.exists(path) || !Files.isReadable(path)) {
            throw new RuntimeException("File not found or not readable: " + pathString);
        }
        return new FileSystemResource(path);
    }

    public void cleanupBatchTemp(UUID batchId) {
        Path batchTempDir = storageProperties.getTempPath().resolve(batchId.toString()).normalize();
        deleteDirectoryRecursively(batchTempDir);
    }

    public void cleanupBatchAll(UUID batchId) {
        deleteDirectoryRecursively(storageProperties.getUploadsPath().resolve(batchId.toString()));
        deleteDirectoryRecursively(storageProperties.getResultsPath().resolve(batchId.toString()));
        deleteDirectoryRecursively(storageProperties.getTempPath().resolve(batchId.toString()));
    }

    private void deleteDirectoryRecursively(Path dir) {
        try {
            if (Files.exists(dir)) {
                try (var stream = Files.walk(dir)) {
                    stream.sorted((a, b) -> b.compareTo(a))
                            .forEach(p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (IOException ignored) {}
                            });
                }
            }
        } catch (Exception e) {
            log.warn("Failed to delete directory {}: {}", dir, e.getMessage());
        }
    }

    public void validatePathTraversal(Path parent, Path child) {
        Path normalizedParent = parent.toAbsolutePath().normalize();
        Path normalizedChild = child.toAbsolutePath().normalize();
        if (!normalizedChild.startsWith(normalizedParent)) {
            throw new SecurityException("Potential path traversal attempt detected: " + child);
        }
    }
}
