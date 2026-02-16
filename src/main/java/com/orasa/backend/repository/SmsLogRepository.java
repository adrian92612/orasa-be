package com.orasa.backend.repository;

import java.util.UUID;
import java.time.OffsetDateTime;
import com.orasa.backend.common.SmsStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.orasa.backend.domain.SmsLogEntity;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SmsLogRepository extends JpaRepository<SmsLogEntity, UUID>{
  Page<SmsLogEntity> findByBusinessId(UUID businessId, Pageable pageable);
  long countByBusinessIdAndCreatedAtBetween(UUID businessId, OffsetDateTime start, OffsetDateTime end);
  long countByBusinessIdAndStatusAndCreatedAtBetween(UUID businessId, SmsStatus status, OffsetDateTime start, OffsetDateTime end);

  @Query("""
      SELECT s FROM SmsLogEntity s
      WHERE s.business.id = :businessId
      AND (cast(:branchId as uuid) IS NULL OR s.appointment.branch.id = :branchId)
      AND (cast(:status as string) IS NULL OR s.status = :status)
      AND (cast(:startDate as offsetdatetime) IS NULL OR s.createdAt >= :startDate)
      AND (cast(:endDate as offsetdatetime) IS NULL OR s.createdAt <= :endDate)
      ORDER BY s.createdAt DESC
      """)
  Page<SmsLogEntity> searchSmsLogs(
      @Param("businessId") UUID businessId,
      @Param("branchId") UUID branchId,
      @Param("status") SmsStatus status,
      @Param("startDate") OffsetDateTime startDate,
      @Param("endDate") OffsetDateTime endDate,
      Pageable pageable);
}
