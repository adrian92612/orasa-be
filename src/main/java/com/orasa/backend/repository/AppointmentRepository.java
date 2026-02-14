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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.orasa.backend.domain.AppointmentEntity;

public interface AppointmentRepository extends JpaRepository<AppointmentEntity, UUID> {
  Page<AppointmentEntity> findByBranchId(UUID branchId, Pageable pageable);
  Page<AppointmentEntity> findByBusinessId(UUID businessId, Pageable pageable);
  Page<AppointmentEntity> findByBranchIdAndStartDateTimeBetween(UUID branchId, OffsetDateTime start, OffsetDateTime end, Pageable pageable);

    @Query("""
      SELECT a
      FROM AppointmentEntity a
      WHERE a.branch.id = :branchId
      AND (COALESCE(:search, '') = '' OR
        a.customerName ILIKE CONCAT('%', :search, '%') OR
        a.customerPhone ILIKE CONCAT('%', :search, '%') OR
        a.notes ILIKE CONCAT('%', :search, '%'))
      AND (:status IS NULL OR a.status = :status)
      AND (:type IS NULL OR a.type = :type)
      AND a.startDateTime >= :startOfDay
      AND a.endDateTime <= :endOfDay
      ORDER BY a.startDateTime ASC
      """)
  Page<AppointmentEntity> searchAppointments(
    @Param("branchId") UUID branchId,
    @Param("search") String search,
    @Param("status") AppointmentStatus status,
    @Param("type") AppointmentType type,
    @Param("startOfDay") OffsetDateTime startOfDay,
    @Param("endOfDay") OffsetDateTime endOfDay,
    Pageable pageable
  );
  

    @Query("""
    SELECT a
    FROM AppointmentEntity a
    WHERE a.business.id = :businessId
    AND (CAST(:search AS string) IS NULL OR
      a.customerName ILIKE CAST(:search AS string) OR
      a.customerPhone ILIKE CAST(:search AS string))
    AND (:status IS NULL OR a.status = :status)
    AND (:type IS NULL OR a.type = :type)
    AND a.startDateTime >= :startOfDay
    AND a.startDateTime <= :endOfDay
    ORDER BY a.startDateTime ASC
    """)
  Page<AppointmentEntity> searchAppointmentsByBusiness(
      @Param("businessId") UUID businessId,
      @Param("search") String search,
      @Param("status") AppointmentStatus status,
      @Param("type") AppointmentType type,
      @Param("startOfDay") OffsetDateTime startOfDay,
      @Param("endOfDay") OffsetDateTime endOfDay,
      Pageable pageable
  );

  // Add entity graph method
  @EntityGraph(attributePaths = {"service", "branch", "business", "selectedReminders"})
  @Query("SELECT a FROM AppointmentEntity a WHERE a.id IN :ids")
  List<AppointmentEntity> findAllByIdWithAssociations(@Param("ids") List<UUID> ids);

  long countByBusinessIdAndStartDateTimeBetween(UUID businessId, OffsetDateTime start, OffsetDateTime end);
  long countByBusinessIdAndStatusAndStartDateTimeBetween(UUID businessId, AppointmentStatus status, OffsetDateTime start, OffsetDateTime end);
  long countByBusinessIdAndTypeAndStartDateTimeBetween(UUID businessId, AppointmentType type, OffsetDateTime start, OffsetDateTime end);

  @Query("SELECT new com.orasa.backend.dto.analytics.DailyStatsDTO(" +
         "CAST(a.startDateTime AS LocalDate), " +
         "COUNT(a), " +
         "SUM(CASE WHEN a.status = 'COMPLETED' THEN 1 ELSE 0 END), " +
         "SUM(CASE WHEN a.status = 'COMPLETED' THEN a.service.basePrice ELSE 0 END)) " +
         "FROM AppointmentEntity a " +
         "WHERE a.business.id = :businessId " +
         "AND a.startDateTime >= :start " +
         "AND a.endDateTime <= :end " +
         "GROUP BY CAST(a.startDateTime AS LocalDate) " +
         "ORDER BY CAST(a.startDateTime AS LocalDate) ASC")
  List<com.orasa.backend.dto.analytics.DailyStatsDTO> getDailyStats(
      @Param("businessId") UUID businessId, 
      @Param("start") OffsetDateTime start, 
      @Param("end") OffsetDateTime end);

  @Query("SELECT new com.orasa.backend.dto.analytics.ServiceStatsDTO(" +
         "s.name, " +
         "COUNT(a), " +
         "CAST(0 AS bigdecimal)) " + // Percentage calculated in service layer
         "FROM AppointmentEntity a " +
         "JOIN a.service s " +
         "WHERE a.business.id = :businessId " +
         "AND a.startDateTime >= :start " +
         "AND a.endDateTime <= :end " +
         "GROUP BY s.name " +
         "ORDER BY COUNT(a) DESC")
  List<com.orasa.backend.dto.analytics.ServiceStatsDTO> getServiceStats(
      @Param("businessId") UUID businessId, 
      @Param("start") OffsetDateTime start, 
      @Param("end") OffsetDateTime end);

  @Query("SELECT new com.orasa.backend.dto.analytics.StatusStatsDTO(" +
         "a.status, " +
         "COUNT(a)) " +
         "FROM AppointmentEntity a " +
         "WHERE a.business.id = :businessId " +
         "AND a.startDateTime >= :start " +
         "AND a.endDateTime <= :end " +
         "GROUP BY a.status")
  List<com.orasa.backend.dto.analytics.StatusStatsDTO> getStatusStats(
      @Param("businessId") UUID businessId, 
      @Param("start") OffsetDateTime start, 
      @Param("end") OffsetDateTime end);
}
