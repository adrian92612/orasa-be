package com.orasa.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.orasa.backend.domain.Service;

public interface ServiceRepository extends JpaRepository<Service, UUID> {
  List<Service> findByBusinessId(UUID businessId);
}
