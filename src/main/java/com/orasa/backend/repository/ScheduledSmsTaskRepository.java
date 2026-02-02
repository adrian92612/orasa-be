package com.orasa.backend.repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.orasa.backend.common.SmsStatus;
import com.orasa.backend.domain.ScheduledSmsTask;

public interface ScheduledSmsTaskRepository extends JpaRepository<ScheduledSmsTask, UUID> {
  Page<ScheduledSmsTask> findByStatusAndScheduledTimeBefore(SmsStatus status, OffsetDateTime time, Pageable pageable);

  Optional<ScheduledSmsTask> findByAppointmentId(UUID appointmentId);
}
