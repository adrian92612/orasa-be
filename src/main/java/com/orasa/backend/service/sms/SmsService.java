package com.orasa.backend.service.sms;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orasa.backend.common.AppointmentStatus;
import com.orasa.backend.common.RequiresActiveSubscription;
import com.orasa.backend.common.SmsStatus;
import com.orasa.backend.common.SmsTaskStatus;
import com.orasa.backend.domain.AppointmentEntity;
import com.orasa.backend.domain.BusinessEntity;
import com.orasa.backend.domain.BusinessReminderConfigEntity;
import com.orasa.backend.domain.ScheduledSmsTaskEntity;
import com.orasa.backend.domain.SmsLogEntity;
import com.orasa.backend.dto.sms.SmsLogResponse;
import com.orasa.backend.exception.ResourceNotFoundException;
import com.orasa.backend.repository.BusinessRepository;
import com.orasa.backend.repository.ScheduledSmsTaskRepository;
import com.orasa.backend.repository.SmsLogRepository;
import com.orasa.backend.service.ReminderConfigService;
import com.orasa.backend.service.SubscriptionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class SmsService {

    private final PhilSmsProvider philSmsProvider;
    private final SmsLogRepository smsLogRepository;
    private final ScheduledSmsTaskRepository scheduledSmsTaskRepository;
    private final BusinessRepository businessRepository;
    private final ReminderConfigService reminderConfigService;
    private final SubscriptionService subscriptionService;


    @Transactional
    public void scheduleRemindersForAppointment(AppointmentEntity appointment) {
        if (!appointment.isRemindersEnabled()) {
            log.info("Reminders disabled for appointment {}", appointment.getId());
            return;
        }

        // Explicitly skip walk-ins (though isRemindersEnabled checks this, adding explicit check for clarity/safety)
        if (appointment.getStatus() == AppointmentStatus.WALK_IN) {
            log.info("Skipping reminders for walk-in appointment {}", appointment.getId());
            return;
        }

        // Check for override
        if (appointment.getReminderLeadTimeMinutesOverride() != null) {
            int leadTime = appointment.getReminderLeadTimeMinutesOverride();
            OffsetDateTime scheduledAt = appointment.getStartDateTime().minusMinutes(leadTime);
            
            if (scheduledAt.isBefore(OffsetDateTime.now())) {
                log.info("Skipping override reminder for appointment {} - scheduled time {} has passed", appointment.getId(), scheduledAt);
                return;
            }

            ScheduledSmsTaskEntity task = ScheduledSmsTaskEntity.builder()
                    .businessId(appointment.getBusiness().getId())
                    .appointment(appointment)
                    .scheduledAt(scheduledAt)
                    .status(SmsTaskStatus.PENDING)
                    .build();
            
            scheduledSmsTaskRepository.save(task);
            log.info("Scheduled SMS reminder (override) for appointment {} at {}", appointment.getId(), scheduledAt);
            return; // Exit after scheduling override
        }

        List<BusinessReminderConfigEntity> configs = reminderConfigService.getEnabledConfigs(
                appointment.getBusiness().getId()
        );

        if (configs.isEmpty()) {
            log.info("No reminder configs found for business {}", appointment.getBusiness().getId());
            return;
        }

        for (BusinessReminderConfigEntity config : configs) {
            if (config.getLeadTimeMinutes() == null) {
                log.warn("Skipping reminder config {} for business {} - leadTimeMinutes is null. Manual migration required.",
                        config.getId(), appointment.getBusiness().getId());
                continue;
            }

            OffsetDateTime scheduledAt = appointment.getStartDateTime()
                    .minusMinutes(config.getLeadTimeMinutes());

            if (scheduledAt.isBefore(OffsetDateTime.now())) {
                log.info("Skipping reminder for appointment {} - scheduled time {} has passed",
                        appointment.getId(), scheduledAt);
                continue;
            }

            ScheduledSmsTaskEntity task = ScheduledSmsTaskEntity.builder()
                    .businessId(appointment.getBusiness().getId())
                    .appointment(appointment)
                    .scheduledAt(scheduledAt)
                    .status(SmsTaskStatus.PENDING)
                    .build();

            scheduledSmsTaskRepository.save(task);
            log.info("Scheduled SMS reminder for appointment {} at {}", appointment.getId(), scheduledAt);
        }
    }

    @Transactional
    public void cancelRemindersForAppointment(UUID appointmentId) {
        List<ScheduledSmsTaskEntity> tasks = scheduledSmsTaskRepository.findByAppointmentId(appointmentId);
        for (ScheduledSmsTaskEntity task : tasks) {
            task.setStatus(SmsTaskStatus.CANCELLED);
        }
        scheduledSmsTaskRepository.saveAll(tasks);
        log.info("Cancelled {} reminders for appointment {}", tasks.size(), appointmentId);
    }

    @Transactional
    public void processPendingReminders() {
        List<ScheduledSmsTaskEntity> dueTasks = scheduledSmsTaskRepository
                .findByStatusAndScheduledAtBefore(SmsTaskStatus.PENDING, OffsetDateTime.now());

        log.info("Processing {} pending SMS reminders", dueTasks.size());

        for (ScheduledSmsTaskEntity task : dueTasks) {
            try {
                processReminder(task);
            } catch (Exception e) {
                log.error("Error processing reminder task {}: {}", task.getId(), e.getMessage());
                task.setStatus(SmsTaskStatus.FAILED);
                scheduledSmsTaskRepository.save(task);
            }
        }
    }

    @Transactional
    @RequiresActiveSubscription
    public SmsLogEntity sendSms(BusinessEntity business, AppointmentEntity appointment, String recipientPhone, String message) {
        // Enforce credit consumption logic
        subscriptionService.consumeSmsCredit(business);

        PhilSmsProvider.SendSmsResult result = philSmsProvider.sendSms(recipientPhone, message);

        SmsLogEntity smsLog = SmsLogEntity.builder()
                .business(business)
                .appointment(appointment)
                .recipientPhone(recipientPhone)
                .messageBody(message)
                .status(result.success() ? SmsStatus.SENT : SmsStatus.FAILED)
                .providerId(result.success() ? result.providerId() : "none")
                .errorMessage(result.errorMessage())
                .build();

        return smsLogRepository.save(smsLog);
    }

    public Page<SmsLogResponse> getSmsLogs(UUID businessId, Pageable pageable) {
        return smsLogRepository.findByBusinessId(businessId, pageable)
                .map(this::mapToResponse);
    }

    public PhilSmsProvider.BalanceResult getBalance() {
        return philSmsProvider.getBalance();
    }

    private void processReminder(ScheduledSmsTaskEntity task) {
        AppointmentEntity appointment = task.getAppointment();

        if (appointment.getStartDateTime().isBefore(OffsetDateTime.now())) {
            log.info("Skipping reminder for appointment {} - already passed", appointment.getId());
            task.setStatus(SmsTaskStatus.SKIPPED);
            scheduledSmsTaskRepository.save(task);
            return;
        }

        String message = buildReminderMessage(appointment);

        BusinessEntity business = businessRepository.findById(task.getBusinessId())
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));

        if (!business.hasActiveSubscription()) {
            log.warn("Skipping reminder for appointment {} - Business {} subscription expired", appointment.getId(), business.getId());
            task.setStatus(SmsTaskStatus.SKIPPED);
            scheduledSmsTaskRepository.save(task);
            return;
        }

        SmsLogEntity smsLog = sendSms(business, appointment, appointment.getCustomerPhone(), message);

        task.setStatus(smsLog.getStatus() == SmsStatus.SENT ? SmsTaskStatus.COMPLETED : SmsTaskStatus.FAILED);
        scheduledSmsTaskRepository.save(task);
    }

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a");

    private String buildReminderMessage(AppointmentEntity appointment) {
    List<BusinessReminderConfigEntity> configs = reminderConfigService.getEnabledConfigs(
            appointment.getBusiness().getId()
    );

    // Update the default fallback to match the new placeholder keys
    String template = (configs == null || configs.isEmpty())
            ? "Reminder: {name}, your {service} is on {date} @ {time} at {businessName}. See you!"
            : configs.get(0).getMessageTemplate();

    return template
            .replace("{name}", appointment.getCustomerName())
            /* Note: Once you link the Service entity to the Appointment, 
               replace "appointment" with appointment.getService().getName() 
            */
            .replace("{service}", "appointment") 
            .replace("{date}", appointment.getStartDateTime().format(DATE_FORMATTER))
            .replace("{time}", appointment.getStartDateTime().format(TIME_FORMATTER))
            .replace("{businessName}", appointment.getBranch().getName());
}

    private SmsLogResponse mapToResponse(SmsLogEntity smsLog) {
        return SmsLogResponse.builder()
                .id(smsLog.getId())
                .businessId(smsLog.getBusiness().getId())
                .appointmentId(smsLog.getAppointment() != null ? smsLog.getAppointment().getId() : null)
                .customerName(smsLog.getAppointment() != null ? smsLog.getAppointment().getCustomerName() : null)
                .recipientPhone(smsLog.getRecipientPhone())
                .messageBody(smsLog.getMessageBody())
                .status(smsLog.getStatus())
                .errorMessage(smsLog.getErrorMessage())
                .createdAt(smsLog.getCreatedAt())
                .build();
    }
}
