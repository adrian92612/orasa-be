package com.orasa.backend.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.orasa.backend.domain.ActivityLogEntity;

public interface ActivityLogRepository extends JpaRepository<ActivityLogEntity, UUID> {
    
    Page<ActivityLogEntity> findByBusinessIdOrderByCreatedAtDesc(UUID businessId, Pageable pageable);
    
    Page<ActivityLogEntity> findByBranchIdOrderByCreatedAtDesc(UUID branchId, Pageable pageable);
    
    Page<ActivityLogEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    
    @Query("""
        SELECT a FROM ActivityLogEntity a
        WHERE a.business.id = :businessId
        AND (:branchId IS NULL OR a.branch.id = :branchId)
        AND (:action IS NULL OR a.action = :action)
        AND (:startDate IS NULL OR a.createdAt >= :startDate)
        AND (:endDate IS NULL OR a.createdAt <= :endDate)
        ORDER BY a.createdAt DESC
        """)
    Page<ActivityLogEntity> searchActivityLogs(
        @Param("businessId") UUID businessId,
        @Param("branchId") UUID branchId,
        @Param("action") String action,
        @Param("startDate") OffsetDateTime startDate,
        @Param("endDate") OffsetDateTime endDate,
        Pageable pageable
    );
}
