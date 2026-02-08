package com.orasa.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.orasa.backend.domain.BusinessEntity;

public interface BusinessRepository extends JpaRepository<BusinessEntity, UUID> {
  Optional<BusinessEntity> findBySlug(String slug);
  Page<BusinessEntity> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
