package com.orasa.backend.repository;

import java.time.OffsetDateTime;
import java.util.UUID;
import com.orasa.backend.common.AppointmentStatus;
import com.orasa.backend.common.AppointmentType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.orasa.backend.domain.Appointment;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {
  Page<Appointment> findByBranchId(UUID branchId, Pageable pageable);
  Page<Appointment> findByBusinessId(UUID businessId, Pageable pageable);
  Page<Appointment> findByBranchIdAndStartDateTimeBetween(UUID branchId, OffsetDateTime start, OffsetDateTime end, Pageable pageable);

    @Query("""
      SELECT a
      FROM Appointment a
      WHERE a.branch.id = :branchId
      AND (COALESCE(:search, '') = '' OR
        LOWER(a.customerName) ILIKE LOWER(CONCAT('%', :search, '%')) OR
        LOWER(a.customerPhone) ILIKE LOWER(CONCAT('%', :search, '%')) OR
        LOWER(a.notes) ILIKE LOWER(CONCAT('%', :search, '%')))
      AND (:startOfDay IS NULL OR a.startDateTime >= :startOfDay)
      AND (:endOfDay IS NULL OR a.endDateTime <= :endOfDay)
      """)
  Page<Appointment> searchAppointments(
    @Param("branchId") UUID branchId,
    @Param("search") String search,
    @Param("startOfDay") OffsetDateTime startOfDay,
    @Param("endOfDay") OffsetDateTime endOfDay,
    Pageable pageable
  );

  long countByBusinessIdAndStartDateTimeBetween(UUID businessId, OffsetDateTime start, OffsetDateTime end);
  long countByBusinessIdAndStatusAndStartDateTimeBetween(UUID businessId, AppointmentStatus status, OffsetDateTime start, OffsetDateTime end);
  long countByBusinessIdAndTypeAndStartDateTimeBetween(UUID businessId, AppointmentType type, OffsetDateTime start, OffsetDateTime end);
  
}
