package com.upscaler.repository;

import com.upscaler.entity.BatchStatus;
import com.upscaler.entity.UpscaleBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UpscaleBatchRepository extends JpaRepository<UpscaleBatch, UUID> {

    @Query("SELECT b FROM UpscaleBatch b LEFT JOIN FETCH b.images WHERE b.id = :id")
    Optional<UpscaleBatch> findByIdWithImages(@Param("id") UUID id);

    List<UpscaleBatch> findAllByOrderByCreatedAtDesc();

    List<UpscaleBatch> findByStatusIn(List<BatchStatus> statuses);
}
