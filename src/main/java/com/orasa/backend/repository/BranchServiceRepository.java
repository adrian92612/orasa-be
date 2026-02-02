package com.orasa.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.orasa.backend.domain.BranchService;

public interface BranchServiceRepository extends JpaRepository<BranchService, UUID> {
  List<BranchService> findByBranchId(UUID branchId);

  List<BranchService> findByServiceId(UUID serviceId);
}
