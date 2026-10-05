package com.upscaler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.upscaler.dto.BatchCreateRequest;
import com.upscaler.dto.BatchResponse;
import com.upscaler.entity.BatchStatus;
import com.upscaler.entity.OutputFormat;
import com.upscaler.entity.ProcessingPreset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.upscale.provider=mock")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BatchControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testEndToEndBatchWorkflow() throws Exception {
        // 1. Create Batch
        BatchCreateRequest createReq = BatchCreateRequest.builder()
                .preset(ProcessingPreset.ADOBE_STOCK)
                .scale(4)
                .model("general")
                .outputFormat(OutputFormat.JPEG)
                .quality(95)
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/upscale/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.scale").value(4))
                .andReturn();

        BatchResponse createdBatch = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                BatchResponse.class
        );
        UUID batchId = createdBatch.getId();
        assertNotNull(batchId);

        // 2. Upload sample image
        BufferedImage img = new BufferedImage(1000, 1000, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", baos);

        MockMultipartFile file = new MockMultipartFile(
                "files",
                "test_landscape.jpg",
                "image/jpeg",
                baos.toByteArray()
        );

        mockMvc.perform(multipart("/api/v1/upscale/batches/" + batchId + "/images")
                        .file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].originalFilename").value("test_landscape.jpg"))
                .andExpect(jsonPath("$[0].inputWidth").value(1000))
                .andExpect(jsonPath("$[0].inputHeight").value(1000));

        // 3. Start Batch
        mockMvc.perform(post("/api/v1/upscale/batches/" + batchId + "/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").isNotEmpty());

        // Give mock processor a brief moment
        Thread.sleep(500);

        // 4. Inspect Batch status
        mockMvc.perform(get("/api/v1/upscale/batches/" + batchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalImages").value(1));

        // 5. Download Results ZIP
        MvcResult zipResult = mockMvc.perform(get("/api/v1/upscale/batches/" + batchId + "/results.zip"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andReturn();

        byte[] zipBytes = zipResult.getResponse().getContentAsByteArray();
        assertTrue(zipBytes.length > 0, "Results ZIP should not be empty");

        // 6. Test Batch Cancellation on a new batch
        MvcResult cancelBatchResult = mockMvc.perform(post("/api/v1/upscale/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID cancelBatchId = objectMapper.readValue(cancelBatchResult.getResponse().getContentAsString(), BatchResponse.class).getId();

        mockMvc.perform(post("/api/v1/upscale/batches/" + cancelBatchId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}
