package com.orasa.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.orasa.backend.domain.Branch;

public interface BranchRepository extends JpaRepository<Branch, UUID> {
  List<Branch> findByBusinessId(UUID businessId);
}
