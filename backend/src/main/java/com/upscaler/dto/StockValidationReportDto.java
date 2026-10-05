package com.upscaler.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockValidationReportDto {
    private boolean resolutionValid;
    private String resolutionMessage;
    private boolean megapixelsValid;
    private String megapixelsMessage;
    private boolean formatValid;
    private String formatMessage;
    private boolean colorProfileValid;
    private String colorProfileMessage;
    private boolean fileSizeValid;
    private String fileSizeMessage;
    private boolean stockReady;
}
