package com.upscaler.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "upscale_image")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpscaleImage {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "batch_id", nullable = false)
    private UpscaleBatch batch;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "input_path", nullable = false, length = 512)
    private String inputPath;

    @Column(name = "output_path", length = 512)
    private String outputPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ImageStatus status;

    @Column(name = "input_width")
    private Integer inputWidth;

    @Column(name = "input_height")
    private Integer inputHeight;

    @Column(name = "input_megapixels", precision = 6, scale = 2)
    private BigDecimal inputMegapixels;

    @Column(name = "input_size")
    private Long inputSize;

    @Column(name = "output_width")
    private Integer outputWidth;

    @Column(name = "output_height")
    private Integer outputHeight;

    @Column(name = "output_megapixels", precision = 6, scale = 2)
    private BigDecimal outputMegapixels;

    @Column(name = "output_size")
    private Long outputSize;

    @Column(name = "output_format", length = 16)
    private String outputFormat;

    @Column(name = "color_profile", length = 32)
    private String colorProfile;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @Column(name = "stock_ready", nullable = false)
    @Builder.Default
    private Boolean stockReady = false;

    @Column(columnDefinition = "TEXT")
    private String error;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = ImageStatus.UPLOADED;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        if (stockReady == null) {
            stockReady = false;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
