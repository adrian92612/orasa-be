package com.orasa.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.orasa.backend.domain.ActivityLog;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {
  Optional<ActivityLog> findByBusinessId(UUID businessId);
}
