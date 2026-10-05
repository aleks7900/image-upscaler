package com.upscaler.entity;

public enum ImageStatus {
    UPLOADED,
    VALIDATING,
    QUEUED,
    PROCESSING,
    POST_PROCESSING,
    VALIDATING_OUTPUT,
    COMPLETED,
    STOCK_VALIDATION_FAILED,
    FAILED,
    CANCELLED
}
