package com.orasa.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.orasa.backend.domain.ServiceEntity;

public interface ServiceRepository extends JpaRepository<ServiceEntity, UUID> {
  List<ServiceEntity> findByBusinessId(UUID businessId);
}

