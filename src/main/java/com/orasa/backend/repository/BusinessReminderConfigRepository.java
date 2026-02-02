package com.orasa.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.orasa.backend.domain.BusinessReminderConfig;

public interface BusinessReminderConfigRepository extends JpaRepository<BusinessReminderConfig, UUID> {
  List<BusinessReminderConfig> findByBusinessId(UUID businessId);
}
