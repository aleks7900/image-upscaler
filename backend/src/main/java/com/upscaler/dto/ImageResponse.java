package com.upscaler.dto;

import com.upscaler.entity.ImageStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageResponse {
    private UUID id;
    private UUID batchId;
    private String originalFilename;
    private ImageStatus status;
    private Integer inputWidth;
    private Integer inputHeight;
    private BigDecimal inputMegapixels;
    private Long inputSize;
    private Integer outputWidth;
    private Integer outputHeight;
    private BigDecimal outputMegapixels;
    private Long outputSize;
    private String outputFormat;
    private String colorProfile;
    private Long processingTimeMs;
    private Boolean stockReady;
    private String error;
    private Instant createdAt;
    private Instant updatedAt;
}
