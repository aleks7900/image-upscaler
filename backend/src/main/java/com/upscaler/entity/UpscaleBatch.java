package com.upscaler.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "upscale_batch")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpscaleBatch {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BatchStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private ProcessingPreset preset = ProcessingPreset.ADOBE_STOCK;

    @Column(nullable = false)
    @Builder.Default
    private Integer scale = 4;

    @Column(nullable = false, length = 64)
    @Builder.Default
    private String model = "general";

    @Enumerated(EnumType.STRING)
    @Column(name = "output_format", nullable = false, length = 16)
    @Builder.Default
    private OutputFormat outputFormat = OutputFormat.JPEG;

    @Column(nullable = false)
    @Builder.Default
    private Integer quality = 95;

    @Column(name = "total_images", nullable = false)
    @Builder.Default
    private Integer totalImages = 0;

    @Column(name = "completed_images", nullable = false)
    @Builder.Default
    private Integer completedImages = 0;

    @Column(name = "failed_images", nullable = false)
    @Builder.Default
    private Integer failedImages = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(columnDefinition = "TEXT")
    private String error;

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<UpscaleImage> images = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = BatchStatus.CREATED;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
