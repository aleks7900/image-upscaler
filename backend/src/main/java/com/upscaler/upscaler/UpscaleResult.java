package com.upscaler.upscaler;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpscaleResult {
    private boolean success;
    private Integer outputWidth;
    private Integer outputHeight;
    private BigDecimal outputMegapixels;
    private Long outputSize;
    private String outputFormat;
    private String colorProfile;
    private Long processingTimeMs;
    private Boolean stockReady;
    private String error;
    private Integer retriesUsed;
    private Integer finalTileSize;
}
