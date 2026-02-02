package com.orasa.backend.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.orasa.backend.domain.SmsLog;

public interface SmsLogRepository extends JpaRepository<SmsLog, UUID>{
  Page<SmsLog> findByBusinessId(UUID businessId, Pageable pageable);
}
