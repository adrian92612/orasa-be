package com.orasa.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.orasa.backend.domain.BranchEntity;

public interface BranchRepository extends JpaRepository<BranchEntity, UUID> {
  List<BranchEntity> findByBusinessId(UUID businessId);
}
