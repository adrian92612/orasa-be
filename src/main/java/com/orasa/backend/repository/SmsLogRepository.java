package com.orasa.backend.repository;

import java.util.UUID;
import java.time.OffsetDateTime;
import com.orasa.backend.common.SmsStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.orasa.backend.domain.SmsLog;

public interface SmsLogRepository extends JpaRepository<SmsLog, UUID>{
  Page<SmsLog> findByBusinessId(UUID businessId, Pageable pageable);
  long countByBusinessIdAndCreatedAtBetween(UUID businessId, OffsetDateTime start, OffsetDateTime end);
  long countByBusinessIdAndStatusAndCreatedAtBetween(UUID businessId, SmsStatus status, OffsetDateTime start, OffsetDateTime end);
}
