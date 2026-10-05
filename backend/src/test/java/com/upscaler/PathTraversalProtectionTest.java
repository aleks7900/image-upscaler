package com.upscaler;

import com.upscaler.service.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = "app.upscale.provider=mock")
@ActiveProfiles("test")
class PathTraversalProtectionTest {

    @Autowired
    private StorageService storageService;

    @Test
    void testRejectsPathTraversalOutsideUploads() {
        var parent = Paths.get("./data/uploads");
        var maliciousChild = Paths.get("./data/uploads/../../etc/passwd");

        assertThrows(SecurityException.class, () ->
                storageService.validatePathTraversal(parent, maliciousChild)
        );
    }
}
