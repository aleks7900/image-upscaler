package com.upscaler.service;

import com.upscaler.entity.ImageStatus;
import com.upscaler.entity.UpscaleImage;
import com.upscaler.repository.UpscaleImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ZipExportService {

    private final UpscaleImageRepository imageRepository;

    public void streamBatchResultsZip(UUID batchId, OutputStream outputStream) throws IOException {
        List<UpscaleImage> images = imageRepository.findByBatchIdOrderByCreatedAtAsc(batchId);

        try (ZipOutputStream zos = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            // Buffer size for streaming
            byte[] buffer = new byte[64 * 1024];
            Set<String> usedEntryNames = new HashSet<>();

            StringBuilder csvBuilder = new StringBuilder();
            csvBuilder.append("original_filename,status,width,height,megapixels,file_size_bytes,format,color_profile,stock_ready,error\n");

            for (UpscaleImage img : images) {
                // Record CSV row
                csvBuilder.append(escapeCsv(img.getOriginalFilename())).append(",")
                        .append(img.getStatus()).append(",")
                        .append(img.getOutputWidth() != null ? img.getOutputWidth() : "").append(",")
                        .append(img.getOutputHeight() != null ? img.getOutputHeight() : "").append(",")
                        .append(img.getOutputMegapixels() != null ? img.getOutputMegapixels() : "").append(",")
                        .append(img.getOutputSize() != null ? img.getOutputSize() : "").append(",")
                        .append(escapeCsv(img.getOutputFormat())).append(",")
                        .append(escapeCsv(img.getColorProfile())).append(",")
                        .append(Boolean.TRUE.equals(img.getStockReady()) ? "READY" : "NOT_READY").append(",")
                        .append(escapeCsv(img.getError()))
                        .append("\n");

                // Stream successful image binary
                if (img.getStatus() == ImageStatus.COMPLETED && img.getOutputPath() != null) {
                    File file = new File(img.getOutputPath());
                    if (file.exists() && file.isFile()) {
                        String entryName = buildSafeZipEntryName(img, usedEntryNames);
                        ZipEntry zipEntry = new ZipEntry(entryName);
                        zipEntry.setSize(file.length());
                        zipEntry.setTime(file.lastModified());
                        zos.putNextEntry(zipEntry);

                        try (FileInputStream fis = new FileInputStream(file)) {
                            int read;
                            while ((read = fis.read(buffer)) != -1) {
                                zos.write(buffer, 0, read);
                            }
                        }
                        zos.closeEntry();
                    }
                }
            }

            // Include Adobe Stock manifest CSV
            byte[] csvBytes = csvBuilder.toString().getBytes(StandardCharsets.UTF_8);
            ZipEntry csvEntry = new ZipEntry("adobe_stock_manifest.csv");
            csvEntry.setSize(csvBytes.length);
            zos.putNextEntry(csvEntry);
            zos.write(csvBytes);
            zos.closeEntry();

            zos.finish();
            zos.flush();
        }
    }

    private String buildSafeZipEntryName(UpscaleImage img, Set<String> usedNames) {
        String baseName = FilenameUtils.getBaseName(img.getOriginalFilename());
        String ext = img.getOutputFormat() != null ? img.getOutputFormat().toLowerCase() : "jpg";
        if ("jpeg".equalsIgnoreCase(ext)) {
            ext = "jpg";
        }

        // Sanitize name
        String safeBase = baseName.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (safeBase.isBlank()) {
            safeBase = img.getId().toString();
        }

        String candidate = safeBase + "_upscaled." + ext;
        int counter = 1;
        while (usedNames.contains(candidate)) {
            candidate = safeBase + "_upscaled_" + counter + "." + ext;
            counter++;
        }

        usedNames.add(candidate);
        return candidate;
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
