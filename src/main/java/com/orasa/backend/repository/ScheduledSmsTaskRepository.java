package com.orasa.backend.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.orasa.backend.common.SmsTaskStatus;
import com.orasa.backend.domain.ScheduledSmsTaskEntity;

public interface ScheduledSmsTaskRepository extends JpaRepository<ScheduledSmsTaskEntity, UUID> {
  List<ScheduledSmsTaskEntity> findByStatusAndScheduledAtBefore(SmsTaskStatus status, OffsetDateTime time);

  List<ScheduledSmsTaskEntity> findByAppointmentId(UUID appointmentId);

  void deleteByAppointmentId(UUID appointmentId);
}
