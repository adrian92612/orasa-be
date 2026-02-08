package com.orasa.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.orasa.backend.domain.BusinessReminderConfigEntity;

public interface BusinessReminderConfigRepository extends JpaRepository<BusinessReminderConfigEntity, UUID> {
  List<BusinessReminderConfigEntity> findByBusinessId(UUID businessId);
}
