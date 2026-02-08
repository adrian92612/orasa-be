package com.orasa.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.orasa.backend.domain.BranchServiceEntity;

public interface BranchServiceRepository extends JpaRepository<BranchServiceEntity, UUID> {
  List<BranchServiceEntity> findByBranchId(UUID branchId);

  List<BranchServiceEntity> findByServiceId(UUID serviceId);
}
