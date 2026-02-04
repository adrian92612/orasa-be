package com.orasa.backend.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.orasa.backend.common.SmsTaskStatus;
import com.orasa.backend.domain.ScheduledSmsTask;

public interface ScheduledSmsTaskRepository extends JpaRepository<ScheduledSmsTask, UUID> {
  List<ScheduledSmsTask> findByStatusAndScheduledAtBefore(SmsTaskStatus status, OffsetDateTime time);

  List<ScheduledSmsTask> findByAppointmentId(UUID appointmentId);

  void deleteByAppointmentId(UUID appointmentId);
}
