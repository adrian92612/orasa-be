package com.orasa.backend.service.sms;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orasa.backend.common.AppointmentType;
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

        // Explicitly skip walk-ins
        if (appointment.getType() == AppointmentType.WALK_IN) {
            log.info("Skipping reminders for walk-in appointment {}", appointment.getId());
            return;
        }

        Set<Integer> uniqueLeadTimes = new HashSet<>();

        // 1. Add additive reminder lead time
        if (appointment.getAdditionalReminderMinutes() != null) {
            uniqueLeadTimes.add(appointment.getAdditionalReminderMinutes());
        }

        // 2. Add selected or default reminder lead times
        List<BusinessReminderConfigEntity> configsToSchedule;
        if (appointment.getSelectedReminders() != null && !appointment.getSelectedReminders().isEmpty()) {
            configsToSchedule = new ArrayList<>(appointment.getSelectedReminders());
        } else {
            configsToSchedule = reminderConfigService.getEnabledConfigs(appointment.getBusiness().getId());
        }

        for (BusinessReminderConfigEntity config : configsToSchedule) {
            if (config.getLeadTimeMinutes() != null) {
                uniqueLeadTimes.add(config.getLeadTimeMinutes());
            }
        }

        if (uniqueLeadTimes.isEmpty()) {
            log.info("No reminder lead times to schedule for appointment {}", appointment.getId());
            return;
        }

        List<ScheduledSmsTaskEntity> tasksToSave = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();

        for (Integer leadTime : uniqueLeadTimes) {
            OffsetDateTime scheduledAt = appointment.getStartDateTime().minusMinutes(leadTime);

            if (scheduledAt.isBefore(now)) {
                log.info("Skipping reminder ({} mins) for appointment {} - scheduled time {} has passed",
                        leadTime, appointment.getId(), scheduledAt);
                continue;
            }

            tasksToSave.add(ScheduledSmsTaskEntity.builder()
                    .businessId(appointment.getBusiness().getId())
                    .appointment(appointment)
                    .scheduledAt(scheduledAt)
                    .status(SmsTaskStatus.PENDING)
                    .build());
            
            log.info("Prepared SMS reminder ({} mins) for appointment {} at {}", 
                leadTime, appointment.getId(), scheduledAt);
        }

        if (!tasksToSave.isEmpty()) {
            scheduledSmsTaskRepository.saveAll(tasksToSave);
            log.info("Scheduled {} unique reminders for appointment {}", tasksToSave.size(), appointment.getId());
        }
    }

    @Transactional
    public void cancelRemindersForAppointment(UUID appointmentId) {
        List<ScheduledSmsTaskEntity> pendingTasks = scheduledSmsTaskRepository
            .findByAppointmentIdAndStatus(appointmentId, SmsTaskStatus.PENDING);
        
        for (ScheduledSmsTaskEntity task : pendingTasks) {
            task.setStatus(SmsTaskStatus.CANCELLED);
        }
        
        scheduledSmsTaskRepository.saveAll(pendingTasks);
        log.info("Cancelled {} pending reminders for appointment {}", pendingTasks.size(), appointmentId);
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
            .replace("{service}", appointment.getService() != null ? appointment.getService().getName() : "appointment") 
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
