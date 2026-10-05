package com.upscaler.service;

import com.upscaler.config.UpscaleProperties;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageValidationService {

    private final UpscaleProperties upscaleProperties;
    private final Tika tika = new Tika();

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    @Getter
    public static class ImageDimensions {
        private final int width;
        private final int height;
        private final BigDecimal megapixels;

        public ImageDimensions(int width, int height) {
            this.width = width;
            this.height = height;
            this.megapixels = BigDecimal.valueOf(width)
                    .multiply(BigDecimal.valueOf(height))
                    .divide(BigDecimal.valueOf(1_000_000), 2, RoundingMode.HALF_UP);
        }
    }

    public void validateUploadPayload(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty.");
        }

        if (file.getSize() > upscaleProperties.getMaxFileSizeBytes()) {
            throw new IllegalArgumentException(String.format("File size %d bytes exceeds maximum allowed %d bytes (%d MB)",
                    file.getSize(), upscaleProperties.getMaxFileSizeBytes(), upscaleProperties.getMaxFileSizeBytes() / (1024 * 1024)));
        }

        // Verify actual binary content with Apache Tika (magic bytes)
        String detectedMimeType;
        try (InputStream is = file.getInputStream()) {
            detectedMimeType = tika.detect(is, file.getOriginalFilename());
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to inspect file magic bytes: " + e.getMessage(), e);
        }

        if (!ALLOWED_MIME_TYPES.contains(detectedMimeType.toLowerCase())) {
            throw new IllegalArgumentException(String.format("Unsupported file format '%s'. Allowed formats: JPEG, PNG, WEBP.", detectedMimeType));
        }

        if ("image/webp".equalsIgnoreCase(detectedMimeType)) {
            try {
                byte[] bytes = file.getBytes();
                ImageDimensions dims = parseWebpDimensions(bytes);
                if (dims.getWidth() <= 0 || dims.getHeight() <= 0) {
                    throw new IllegalArgumentException("Invalid image dimensions in WebP file: " + file.getOriginalFilename());
                }
                return;
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to read WebP file bytes: " + e.getMessage(), e);
            }
        }

        // Verify that the payload is readable and not truncated/corrupted
        try (InputStream is = file.getInputStream();
             ImageInputStream iis = ImageIO.createImageInputStream(is)) {
            if (iis == null) {
                throw new IllegalArgumentException("Cannot parse image stream: " + file.getOriginalFilename());
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("Corrupted image data or unsupported header: " + file.getOriginalFilename());
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0) {
                    throw new IllegalArgumentException("Invalid image dimensions in file: " + file.getOriginalFilename());
                }
            } finally {
                reader.dispose();
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Corrupted or unreadable image file: " + file.getOriginalFilename() + " (" + e.getMessage() + ")", e);
        }
    }

    public ImageDimensions inspectAndValidateStoredImage(Path imagePath) {
        File file = imagePath.toFile();
        if (!file.exists()) {
            throw new IllegalArgumentException("Stored image file not found: " + imagePath);
        }

        String fileName = imagePath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".webp")) {
            try {
                byte[] bytes = java.nio.file.Files.readAllBytes(imagePath);
                ImageDimensions dims = parseWebpDimensions(bytes);
                validateDimensionsAndBomb(dims.getWidth(), dims.getHeight());
                return dims;
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to read WebP image: " + e.getMessage(), e);
            }
        }

        try (ImageInputStream in = ImageIO.createImageInputStream(file)) {
            if (in == null) {
                throw new IllegalArgumentException("Image stream could not be opened. Corrupted image file: " + imagePath.getFileName());
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("No suitable image reader found for file: " + imagePath.getFileName());
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                validateDimensionsAndBomb(width, height);
                return new ImageDimensions(width, height);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read image headers: " + e.getMessage(), e);
        }
    }

    private void validateDimensionsAndBomb(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid dimensions: " + width + "x" + height);
        }

        if (width > upscaleProperties.getMaxDimension() || height > upscaleProperties.getMaxDimension()) {
            throw new IllegalArgumentException(String.format("Image dimensions (%dx%d) exceed maximum permitted dimension of %dpx",
                    width, height, upscaleProperties.getMaxDimension()));
        }

        long pixelCount = (long) width * height;
        if (pixelCount > upscaleProperties.getMaxPixelCount()) {
            throw new IllegalArgumentException(String.format("Pixel count (%d) exceeds decompression bomb threshold of %d pixels",
                    pixelCount, upscaleProperties.getMaxPixelCount()));
        }
    }

    public static ImageDimensions parseWebpDimensions(byte[] bytes) {
        if (bytes == null || bytes.length < 30) {
            throw new IllegalArgumentException("Invalid WebP file: header too short");
        }
        if (bytes[0] != 'R' || bytes[1] != 'I' || bytes[2] != 'F' || bytes[3] != 'F' ||
                bytes[8] != 'W' || bytes[9] != 'E' || bytes[10] != 'B' || bytes[11] != 'P') {
            throw new IllegalArgumentException("Invalid WebP header signature");
        }

        String format = new String(bytes, 12, 4, java.nio.charset.StandardCharsets.US_ASCII);
        if ("VP8X".equals(format)) {
            int width = 1 + ((bytes[24] & 0xFF) | ((bytes[25] & 0xFF) << 8) | ((bytes[26] & 0xFF) << 16));
            int height = 1 + ((bytes[27] & 0xFF) | ((bytes[28] & 0xFF) << 8) | ((bytes[29] & 0xFF) << 16));
            return new ImageDimensions(width, height);
        } else if ("VP8L".equals(format)) {
            if (bytes[20] != 0x2f) {
                throw new IllegalArgumentException("Invalid VP8L signature");
            }
            int b1 = bytes[21] & 0xFF;
            int b2 = bytes[22] & 0xFF;
            int b3 = bytes[23] & 0xFF;
            int b4 = bytes[24] & 0xFF;
            int width = 1 + (((b2 & 0x3F) << 8) | b1);
            int height = 1 + (((b4 & 0xF) << 10) | (b3 << 2) | ((b2 & 0xC0) >> 6));
            return new ImageDimensions(width, height);
        } else if ("VP8 ".equals(format)) {
            int width = ((bytes[27] & 0xFF) << 8) | (bytes[26] & 0xFF);
            int height = ((bytes[29] & 0xFF) << 8) | (bytes[28] & 0xFF);
            return new ImageDimensions(width & 0x3FFF, height & 0x3FFF);
        }
        throw new IllegalArgumentException("Unsupported WebP chunk format: " + format);
    }
}
