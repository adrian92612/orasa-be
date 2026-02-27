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
        AND (cast(:branchId as uuid) IS NULL OR a.branch.id = :branchId)
        AND (:hasActions = false OR a.action IN :actions)
        AND (cast(:startDate as offsetdatetime) IS NULL OR a.createdAt >= :startDate)
        AND (cast(:endDate as offsetdatetime) IS NULL OR a.createdAt <= :endDate)
        ORDER BY a.createdAt DESC
        """)
    Page<ActivityLogEntity> searchActivityLogs(
        @Param("businessId") UUID businessId,
        @Param("branchId") UUID branchId,
        @Param("hasActions") boolean hasActions,
        @Param("actions") java.util.List<String> actions,
        @Param("startDate") OffsetDateTime startDate,
        @Param("endDate") OffsetDateTime endDate,
        Pageable pageable
    );
}
