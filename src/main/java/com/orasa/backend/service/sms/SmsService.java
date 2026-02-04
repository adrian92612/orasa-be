package com.orasa.backend.service.sms;

import java.time.OffsetDateTime;
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
import com.orasa.backend.domain.Appointment;
import com.orasa.backend.domain.Business;
import com.orasa.backend.domain.BusinessReminderConfig;
import com.orasa.backend.domain.ScheduledSmsTask;
import com.orasa.backend.domain.SmsLog;
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
    public void scheduleRemindersForAppointment(Appointment appointment) {
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
        if (appointment.getReminderLeadTimeOverride() != null) {
            int leadTime = appointment.getReminderLeadTimeOverride();
            OffsetDateTime scheduledAt = appointment.getStartDateTime().minusHours(leadTime);
            
            if (scheduledAt.isBefore(OffsetDateTime.now())) {
                log.info("Skipping override reminder for appointment {} - scheduled time {} has passed", appointment.getId(), scheduledAt);
                return;
            }

            ScheduledSmsTask task = ScheduledSmsTask.builder()
                    .businessId(appointment.getBusiness().getId())
                    .appointment(appointment)
                    .scheduledAt(scheduledAt)
                    .status(SmsTaskStatus.PENDING)
                    .build();
            
            scheduledSmsTaskRepository.save(task);
            log.info("Scheduled SMS reminder (override) for appointment {} at {}", appointment.getId(), scheduledAt);
            return; // Exit after scheduling override
        }

        List<BusinessReminderConfig> configs = reminderConfigService.getEnabledConfigs(
                appointment.getBusiness().getId()
        );

        if (configs.isEmpty()) {
            log.info("No reminder configs found for business {}", appointment.getBusiness().getId());
            return;
        }

        for (BusinessReminderConfig config : configs) {
            OffsetDateTime scheduledAt = appointment.getStartDateTime()
                    .minusHours(config.getLeadTimeHours());

            if (scheduledAt.isBefore(OffsetDateTime.now())) {
                log.info("Skipping reminder for appointment {} - scheduled time {} has passed",
                        appointment.getId(), scheduledAt);
                continue;
            }

            ScheduledSmsTask task = ScheduledSmsTask.builder()
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
        List<ScheduledSmsTask> tasks = scheduledSmsTaskRepository.findByAppointmentId(appointmentId);
        for (ScheduledSmsTask task : tasks) {
            task.setStatus(SmsTaskStatus.CANCELLED);
        }
        scheduledSmsTaskRepository.saveAll(tasks);
        log.info("Cancelled {} reminders for appointment {}", tasks.size(), appointmentId);
    }

    @Transactional
    public void processPendingReminders() {
        List<ScheduledSmsTask> dueTasks = scheduledSmsTaskRepository
                .findByStatusAndScheduledAtBefore(SmsTaskStatus.PENDING, OffsetDateTime.now());

        log.info("Processing {} pending SMS reminders", dueTasks.size());

        for (ScheduledSmsTask task : dueTasks) {
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
    public SmsLog sendSms(Business business, Appointment appointment, String recipientPhone, String message) {
        // Enforce credit consumption logic
        subscriptionService.consumeSmsCredit(business);

        PhilSmsProvider.SendSmsResult result = philSmsProvider.sendSms(recipientPhone, message);

        SmsLog smsLog = SmsLog.builder()
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

    private void processReminder(ScheduledSmsTask task) {
        Appointment appointment = task.getAppointment();

        if (appointment.getStartDateTime().isBefore(OffsetDateTime.now())) {
            log.info("Skipping reminder for appointment {} - already passed", appointment.getId());
            task.setStatus(SmsTaskStatus.SKIPPED);
            scheduledSmsTaskRepository.save(task);
            return;
        }

        String message = buildReminderMessage(appointment);

        Business business = businessRepository.findById(task.getBusinessId())
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));

        if (!business.hasActiveSubscription()) {
            log.warn("Skipping reminder for appointment {} - Business {} subscription expired", appointment.getId(), business.getId());
            task.setStatus(SmsTaskStatus.SKIPPED);
            scheduledSmsTaskRepository.save(task);
            return;
        }

        SmsLog smsLog = sendSms(business, appointment, appointment.getCustomerPhone(), message);

        task.setStatus(smsLog.getStatus() == SmsStatus.SENT ? SmsTaskStatus.COMPLETED : SmsTaskStatus.FAILED);
        scheduledSmsTaskRepository.save(task);
    }

    private String buildReminderMessage(Appointment appointment) {
        List<BusinessReminderConfig> configs = reminderConfigService.getEnabledConfigs(
                appointment.getBusiness().getId()
        );

        String template = configs.isEmpty() 
                ? "Reminder: You have an appointment on {date} at {time}. See you soon!"
                : configs.get(0).getMessageTemplate();

        return template
                .replace("{customer_name}", appointment.getCustomerName())
                .replace("{date}", appointment.getStartDateTime().toLocalDate().toString())
                .replace("{time}", appointment.getStartDateTime().toLocalTime().toString())
                .replace("{branch_name}", appointment.getBranch().getName());
    }

    private SmsLogResponse mapToResponse(SmsLog smsLog) {
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
