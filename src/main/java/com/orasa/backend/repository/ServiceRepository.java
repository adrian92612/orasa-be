package com.orasa.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.orasa.backend.domain.ServiceEntity;

public interface ServiceRepository extends JpaRepository<ServiceEntity, UUID> {
    List<ServiceEntity> findByBusinessId(UUID businessId);

    @Query("SELECT s FROM ServiceEntity s WHERE s.businessId = :businessId AND EXISTS (SELECT 1 FROM BranchServiceEntity bs WHERE bs.service.id = s.id AND bs.branchId = :branchId AND bs.isActive = true)")
    List<ServiceEntity> findServicesForBranch(@Param("businessId") UUID businessId, @Param("branchId") UUID branchId);
}

