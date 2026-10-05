package com.upscaler.dto;

import com.upscaler.entity.OutputFormat;
import com.upscaler.entity.ProcessingPreset;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchCreateRequest {

    @Builder.Default
    private ProcessingPreset preset = ProcessingPreset.ADOBE_STOCK;

    @NotNull
    @Min(2)
    @Max(4)
    @Builder.Default
    private Integer scale = 4;

    @Builder.Default
    private String model = "general";

    @Builder.Default
    private OutputFormat outputFormat = OutputFormat.JPEG;

    @Min(50)
    @Max(100)
    @Builder.Default
    private Integer quality = 95;
}
