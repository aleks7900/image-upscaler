package com.upscaler.entity;

public enum BatchStatus {
    CREATED,
    UPLOADING,
    QUEUED,
    PROCESSING,
    COMPLETED,
    PARTIALLY_COMPLETED,
    FAILED,
    CANCELLED
}
