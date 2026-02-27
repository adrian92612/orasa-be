package com.orasa.backend.service.sms;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.orasa.backend.common.AppointmentStatus;
import com.orasa.backend.common.SmsStatus;
import com.orasa.backend.common.SmsTaskStatus;
import com.orasa.backend.domain.AppointmentEntity;
import com.orasa.backend.domain.BusinessEntity;
import com.orasa.backend.domain.ScheduledSmsTaskEntity;
import com.orasa.backend.domain.SmsLogEntity;
import com.orasa.backend.dto.sms.SmsReminderTask;
import com.orasa.backend.exception.ResourceNotFoundException;
import com.orasa.backend.repository.AppointmentRepository;
import com.orasa.backend.repository.BusinessRepository;
import com.orasa.backend.repository.ScheduledSmsTaskRepository;
import com.orasa.backend.repository.SmsLogRepository;
import com.orasa.backend.service.SubscriptionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Helper service for SMS task processing.
 * Extracted from SmsService to ensure @Transactional annotations 
 * work correctly (Spring AOP proxy limitation with self-invocation).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmsTaskHelper {

    private final SmsLogRepository smsLogRepository;
    private final BusinessRepository businessRepository;
    private final AppointmentRepository appointmentRepository;
    private final SubscriptionService subscriptionService;
    private final ScheduledSmsTaskRepository scheduledSmsTaskRepository;
    private final Clock clock;

    public record TaskPreparationResult(ScheduledSmsTaskEntity scheduledTask, SmsLogEntity smsLog) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TaskPreparationResult prepareTask(SmsReminderTask task, String message) {
        UUID appointmentId = task.getAppointmentId();
        UUID scheduledTaskId = task.getScheduledTaskId();

        ScheduledSmsTaskEntity scheduledTask = scheduledSmsTaskRepository.findById(scheduledTaskId)
                .orElse(null);

        if (scheduledTask == null) {
            log.warn("Skipping reminder task - ScheduledTask record {} not found", scheduledTaskId);
            return null;
        }

        if (scheduledTask.getStatus() != SmsTaskStatus.PENDING) {
            log.info("Skipping task {} - Status is already {}", scheduledTaskId, scheduledTask.getStatus());
            return null;
        }

        AppointmentEntity appointment = appointmentRepository.findById(appointmentId)
                .orElse(null);

        if (appointment == null) {
            log.warn("Skipping reminder for appointment {} - Appointment not found (deleted?)", appointmentId);
            scheduledTask.setStatus(SmsTaskStatus.FAILED);
            scheduledSmsTaskRepository.save(scheduledTask);
            return null;
        }

        if (appointment.getStatus() == AppointmentStatus.CANCELLED || appointment.getStatus() == AppointmentStatus.NO_SHOW) {
            log.info("Skipping reminder for appointment {} - Appointment status is {}", appointmentId, appointment.getStatus());
            scheduledTask.setStatus(SmsTaskStatus.CANCELLED);
            scheduledSmsTaskRepository.save(scheduledTask);
            return null;
        }

        if (!appointment.isRemindersEnabled()) {
             log.info("Skipping reminder for appointment {} - Reminders disabled", appointmentId);
             scheduledTask.setStatus(SmsTaskStatus.SKIPPED);
             scheduledSmsTaskRepository.save(scheduledTask);
             return null;
        }

        OffsetDateTime currentTargetTime = appointment.getStartDateTime().minusMinutes(task.getLeadTimeMinutes());
        if (!currentTargetTime.isEqual(task.getScheduledAt())) {
             log.info("Skipping stale reminder for appointment {} - Appointment time changed. Task Scheduled: {}, Current Target: {}", 
                 appointmentId, task.getScheduledAt(), currentTargetTime);
             scheduledTask.setStatus(SmsTaskStatus.SKIPPED);
             scheduledSmsTaskRepository.save(scheduledTask);
             return null;
        }
        
        if (appointment.getStartDateTime().isBefore(OffsetDateTime.now(clock))) {
            log.info("Skipping reminder for appointment {} - Appointment already started/passed", appointmentId);
            scheduledTask.setStatus(SmsTaskStatus.SKIPPED);
            scheduledSmsTaskRepository.save(scheduledTask);
            return null;
        }

        BusinessEntity business = businessRepository.findById(task.getBusinessId())
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));

        if (!business.hasActiveSubscription()) {
            log.warn("Skipping reminder for appointment {} - Business {} subscription expired", appointmentId, business.getId());
            scheduledTask.setStatus(SmsTaskStatus.FAILED);
            scheduledSmsTaskRepository.save(scheduledTask);
            return null;
        }

        // Mark as PROCESSING before sending to prevent duplicate sends on retry
        scheduledTask.setStatus(SmsTaskStatus.PROCESSING);
        scheduledSmsTaskRepository.save(scheduledTask);

        // Create the Log (The Receipt)
        SmsLogEntity smsLog = SmsLogEntity.builder()
                .business(business)
                .appointment(appointment)
                .recipientPhone(appointment.getCustomerPhone())
                .messageBody(message)
                .status(SmsStatus.PENDING)
                .providerId("none")
                .build();
        smsLog = smsLogRepository.save(smsLog);

        // Consume credit upfront per MVP rule
        try {
            subscriptionService.consumeSmsCredit(business);
            return new TaskPreparationResult(scheduledTask, smsLog);
        } catch (Exception e) {
            log.warn("Failed to consume credit for appointment {}: {}", appointmentId, e.getMessage());
            smsLog.setStatus(SmsStatus.FAILED);
            smsLog.setErrorMessage(e.getMessage());
            smsLogRepository.save(smsLog);
            
            scheduledTask.setStatus(SmsTaskStatus.FAILED);
            scheduledSmsTaskRepository.save(scheduledTask);
            return null;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeTaskStatus(ScheduledSmsTaskEntity scheduledTask, SmsLogEntity smsLog, PhilSmsProvider.SendSmsResult result) {
        // Re-fetch entities to ensure they're attached to this transaction's persistence context
        scheduledTask = scheduledSmsTaskRepository.findById(scheduledTask.getId())
                .orElseThrow(() -> new ResourceNotFoundException("ScheduledSmsTask not found"));
        smsLog = smsLogRepository.findById(smsLog.getId())
                .orElseThrow(() -> new ResourceNotFoundException("SmsLog not found"));

        if (result.success()) {
            smsLog.setStatus(SmsStatus.DELIVERED);
            scheduledTask.setStatus(SmsTaskStatus.COMPLETED);
        } else {
            smsLog.setStatus(SmsStatus.FAILED);
            scheduledTask.setStatus(SmsTaskStatus.FAILED);
        }

        smsLog.setProviderId(result.providerId() != null ? result.providerId() : "none");
        smsLog.setProviderResponse(result.rawResponse());
        smsLog.setErrorMessage(result.errorMessage());

        smsLogRepository.save(smsLog);
        scheduledSmsTaskRepository.save(scheduledTask);
    }
}
