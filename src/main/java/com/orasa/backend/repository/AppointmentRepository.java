package com.orasa.backend.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.orasa.backend.common.AppointmentStatus;
import com.orasa.backend.common.AppointmentType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.orasa.backend.domain.AppointmentEntity;
import com.orasa.backend.dto.analytics.DailyStatsDTO;
import com.orasa.backend.dto.analytics.ServiceStatsDTO;
import com.orasa.backend.dto.analytics.ServiceNoShowStatsDTO;
import com.orasa.backend.dto.analytics.StatusStatsDTO;

public interface AppointmentRepository extends JpaRepository<AppointmentEntity, UUID>, JpaSpecificationExecutor<AppointmentEntity> {
  @EntityGraph(attributePaths = {"branch", "business", "services"})
  Page<AppointmentEntity> findByBranchId(UUID branchId, Pageable pageable);

  @EntityGraph(attributePaths = {"branch", "business", "services"})
  Page<AppointmentEntity> findByBusinessId(UUID businessId, Pageable pageable);

  @EntityGraph(attributePaths = {"branch", "business", "services"})
  Page<AppointmentEntity> findByBusinessIdAndBranchIdIn(UUID businessId, List<UUID> branchIds, Pageable pageable);

  @EntityGraph(attributePaths = {"branch", "business", "services"})
  @Query("SELECT a FROM AppointmentEntity a WHERE a.id IN :ids")
  List<AppointmentEntity> findAllByIdWithAssociations(@Param("ids") List<UUID> ids);

  @Query("""
      SELECT COUNT(a) FROM AppointmentEntity a 
      WHERE a.business.id = :businessId 
      AND (CAST(:branchId AS uuid) IS NULL OR a.branch.id = :branchId) 
      AND a.startDateTime >= :start 
      AND a.startDateTime <= :end
  """)
  long countByBusinessIdAndBranchIdOptionalAndStartDateTimeBetween(
      @Param("businessId") UUID businessId, 
      @Param("branchId") UUID branchId, 
      @Param("start") OffsetDateTime start, 
      @Param("end") OffsetDateTime end);

  @Query("""
      SELECT COUNT(a) FROM AppointmentEntity a 
      WHERE a.business.id = :businessId 
      AND (CAST(:branchId AS uuid) IS NULL OR a.branch.id = :branchId) 
      AND a.status = :status 
      AND a.startDateTime >= :start 
      AND a.startDateTime <= :end
  """)
  long countByBusinessIdAndBranchIdOptionalAndStatusAndStartDateTimeBetween(
      @Param("businessId") UUID businessId, 
      @Param("branchId") UUID branchId, 
      @Param("status") AppointmentStatus status, 
      @Param("start") OffsetDateTime start, 
      @Param("end") OffsetDateTime end);

  @Query("""
      SELECT COUNT(a) FROM AppointmentEntity a 
      WHERE a.business.id = :businessId 
      AND (CAST(:branchId AS uuid) IS NULL OR a.branch.id = :branchId) 
      AND a.type = :type 
      AND a.startDateTime >= :start 
      AND a.startDateTime <= :end
  """)
  long countByBusinessIdAndBranchIdOptionalAndTypeAndStartDateTimeBetween(
      @Param("businessId") UUID businessId, 
      @Param("branchId") UUID branchId, 
      @Param("type") AppointmentType type, 
      @Param("start") OffsetDateTime start, 
      @Param("end") OffsetDateTime end);

  @Query("""
      SELECT new com.orasa.backend.dto.analytics.DailyStatsDTO(
          CAST(a.startDateTime AS LocalDate), 
          COUNT(DISTINCT a), 
          SUM(CASE WHEN a.status = 'COMPLETED' THEN 1 ELSE 0 END)) 
      FROM AppointmentEntity a 
      WHERE a.business.id = :businessId 
      AND (CAST(:branchId AS uuid) IS NULL OR a.branch.id = :branchId) 
      AND a.startDateTime >= :start 
      AND a.startDateTime <= :end 
      GROUP BY CAST(a.startDateTime AS LocalDate) 
      ORDER BY CAST(a.startDateTime AS LocalDate) ASC
  """)
  List<DailyStatsDTO> getDailyStats(
      @Param("businessId") UUID businessId, 
      @Param("branchId") UUID branchId, 
      @Param("start") OffsetDateTime start, 
      @Param("end") OffsetDateTime end);

  @Query("""
      SELECT new com.orasa.backend.dto.analytics.ServiceStatsDTO(
          s.name, 
          COUNT(a), 
          CAST(0 AS bigdecimal)) 
      FROM AppointmentEntity a 
      JOIN a.services s 
      WHERE a.business.id = :businessId 
      AND (CAST(:branchId AS uuid) IS NULL OR a.branch.id = :branchId) 
      AND a.startDateTime >= :start 
      AND a.startDateTime <= :end 
      GROUP BY s.name 
      ORDER BY COUNT(a) DESC
      LIMIT 5
  """)
  List<ServiceStatsDTO> getServiceStats(
      @Param("businessId") UUID businessId, 
      @Param("branchId") UUID branchId, 
      @Param("start") OffsetDateTime start, 
      @Param("end") OffsetDateTime end);

  @Query("""
      SELECT new com.orasa.backend.dto.analytics.ServiceNoShowStatsDTO(
          s.name, 
          COUNT(a), 
          SUM(CASE WHEN a.status = :noShowStatus THEN 1L ELSE 0L END),
          CAST(0 AS bigdecimal)) 
      FROM AppointmentEntity a 
      JOIN a.services s 
      WHERE a.business.id = :businessId 
      AND (CAST(:branchId AS uuid) IS NULL OR a.branch.id = :branchId) 
      AND a.startDateTime >= :start 
      AND a.startDateTime <= :end 
      GROUP BY s.name 
      ORDER BY COUNT(a) DESC
  """)
  List<ServiceNoShowStatsDTO> getServiceNoShowStats(
      @Param("businessId") UUID businessId, 
      @Param("branchId") UUID branchId, 
      @Param("start") OffsetDateTime start, 
      @Param("end") OffsetDateTime end,
      @Param("noShowStatus") AppointmentStatus noShowStatus);

  @Query("""
      SELECT new com.orasa.backend.dto.analytics.StatusStatsDTO(
          a.status, 
          COUNT(a)) 
      FROM AppointmentEntity a 
      WHERE a.business.id = :businessId 
      AND (CAST(:branchId AS uuid) IS NULL OR a.branch.id = :branchId) 
      AND a.startDateTime >= :start 
      AND a.startDateTime <= :end 
      GROUP BY a.status
  """)
  List<StatusStatsDTO> getStatusStats(
      @Param("businessId") UUID businessId, 
      @Param("branchId") UUID branchId, 
      @Param("start") OffsetDateTime start, 
      @Param("end") OffsetDateTime end);

    @Query(value = """
        SELECT EXTRACT(DOW FROM start_date_time)::int AS day_of_week, COUNT(*) AS count 
        FROM appointments 
        WHERE is_deleted = false 
        AND business_id = :businessId 
        AND (:branchId IS NULL OR branch_id = CAST(:branchId AS uuid)) 
        AND start_date_time >= :start 
        AND start_date_time <= :end 
        GROUP BY EXTRACT(DOW FROM start_date_time) 
        ORDER BY EXTRACT(DOW FROM start_date_time) ASC
        """, nativeQuery = true)
    List<Object[]> getWeekdayStats(
        @Param("businessId") UUID businessId,
        @Param("branchId") UUID branchId,
        @Param("start") OffsetDateTime start,
        @Param("end") OffsetDateTime end);
}
