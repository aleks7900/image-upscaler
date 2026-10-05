package com.upscaler.repository;

import com.upscaler.entity.ImageStatus;
import com.upscaler.entity.UpscaleImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UpscaleImageRepository extends JpaRepository<UpscaleImage, UUID> {

    List<UpscaleImage> findByBatchIdOrderByCreatedAtAsc(UUID batchId);

    List<UpscaleImage> findByBatchIdAndStatus(UUID batchId, ImageStatus status);

    List<UpscaleImage> findByStatusInOrderByCreatedAtAsc(List<ImageStatus> statuses);

    long countByBatchIdAndStatus(UUID batchId, ImageStatus status);
}
