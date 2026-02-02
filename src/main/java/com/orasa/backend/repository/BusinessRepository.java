package com.orasa.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.orasa.backend.domain.Business;

public interface BusinessRepository extends JpaRepository<Business, UUID> {
  Optional<Business> findBySlug(String slug);
}
