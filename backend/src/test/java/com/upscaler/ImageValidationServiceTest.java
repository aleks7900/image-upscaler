package com.upscaler;

import com.upscaler.service.ImageValidationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "app.upscale.provider=mock")
@ActiveProfiles("test")
class ImageValidationServiceTest {

    @Autowired
    private ImageValidationService validationService;

    @Test
    void testRejectsFakeExtensionFile() {
        // Text file named fake_photo.png
        byte[] fakeContent = "This is not an image file but plain text.".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "fake_photo.png",
                "image/png",
                fakeContent
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                validationService.validateUploadPayload(file)
        );
        assertTrue(ex.getMessage().contains("Unsupported file format"),
                "Tika magic byte check must reject non-image file with spoofed extension");
    }

    @Test
    void testRejectsCorruptedImageBytes() {
        byte[] corruptedBytes = new byte[]{ (byte) 0x89, 0x50, 0x4E, 0x47, 0x00, 0x00 }; // broken PNG header
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "corrupted.png",
                "image/png",
                corruptedBytes
        );

        assertThrows(IllegalArgumentException.class, () ->
                validationService.validateUploadPayload(file)
        );
    }

    @Test
    void testAcceptsValidJpegAndPng() throws IOException {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);

        MockMultipartFile file = new MockMultipartFile(
                "files",
                "sample.png",
                "image/png",
                baos.toByteArray()
        );

        assertDoesNotThrow(() -> validationService.validateUploadPayload(file));
    }
}
