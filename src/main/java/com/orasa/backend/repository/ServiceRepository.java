package com.orasa.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.orasa.backend.domain.ServiceOffering;

public interface ServiceRepository extends JpaRepository<ServiceOffering, UUID> {
  List<ServiceOffering> findByBusinessId(UUID businessId);
}

