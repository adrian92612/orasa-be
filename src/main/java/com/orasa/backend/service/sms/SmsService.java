package com.orasa.backend.service.sms;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orasa.backend.common.AppointmentStatus;
import com.orasa.backend.common.AppointmentType;
import com.orasa.backend.common.RequiresActiveSubscription;
import com.orasa.backend.common.SmsStatus;
import com.orasa.backend.domain.AppointmentEntity;
import com.orasa.backend.domain.BusinessEntity;
import com.orasa.backend.domain.BusinessReminderConfigEntity;
import com.orasa.backend.domain.SmsLogEntity;
import com.orasa.backend.dto.sms.SmsLogResponse;
import com.orasa.backend.dto.sms.SmsReminderTask;
import com.orasa.backend.exception.ResourceNotFoundException;
import com.orasa.backend.repository.AppointmentRepository;
import com.orasa.backend.repository.BusinessRepository;
import com.orasa.backend.repository.SmsLogRepository;
import com.orasa.backend.repository.ScheduledSmsTaskRepository;
import com.orasa.backend.service.ReminderConfigService;
import com.orasa.backend.service.SubscriptionService;
import com.orasa.backend.common.SmsTaskStatus;
import com.orasa.backend.domain.ScheduledSmsTaskEntity;
import com.orasa.backend.exception.SubscriptionExpiredException;
import com.orasa.backend.config.TimeConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class SmsService {

    private final PhilSmsProvider philSmsProvider;
    private final SmsLogRepository smsLogRepository;
    private final BusinessRepository businessRepository;
    private final AppointmentRepository appointmentRepository;
    private final ReminderConfigService reminderConfigService;
    private final SubscriptionService subscriptionService;
    private final ScheduledSmsTaskRepository scheduledSmsTaskRepository;
    private final RedissonClient redissonClient;
    private final Clock clock;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a");

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
        if (appointment.getSelectedReminders() != null) {
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

        RBlockingQueue<SmsReminderTask> blockingQueue = redissonClient.getBlockingQueue("smsRemindersQueue");
        @SuppressWarnings("deprecation")
        RDelayedQueue<SmsReminderTask> delayedQueue = redissonClient.getDelayedQueue(blockingQueue);
        
        OffsetDateTime now = OffsetDateTime.now(clock);

        for (Integer leadTime : uniqueLeadTimes) {
            OffsetDateTime scheduledAt = appointment.getStartDateTime().minusMinutes(leadTime);

            if (scheduledAt.isBefore(now)) {
                log.info("Skipping reminder ({} mins) for appointment {} - scheduled time {} has passed",
                        leadTime, appointment.getId(), scheduledAt);
                continue;
            }

            // Create ScheduledSmsTaskEntity (The Plan)
            ScheduledSmsTaskEntity scheduledTask = ScheduledSmsTaskEntity.builder()
                    .business(appointment.getBusiness())
                    .appointment(appointment)
                    .scheduledAt(scheduledAt)
                    .status(SmsTaskStatus.PENDING)
                    .leadTimeMinutes(leadTime)
                    .build();
            scheduledTask = scheduledSmsTaskRepository.save(scheduledTask);

            long delay = ChronoUnit.MILLIS.between(now, scheduledAt);
            if (delay < 0) delay = 0;

            SmsReminderTask task = SmsReminderTask.builder()
                    .appointmentId(appointment.getId())
                    .businessId(appointment.getBusiness().getId())
                    .scheduledTaskId(scheduledTask.getId())
                    .leadTimeMinutes(leadTime)
                    .scheduledAt(scheduledAt)
                    .build();

            delayedQueue.offer(task, delay, TimeUnit.MILLISECONDS);
            
            log.info("Scheduled SMS reminder ({} mins) for appointment {} at {}", 
                leadTime, appointment.getId(), scheduledAt);
        }
    }

    @Transactional
    public void cancelRemindersForAppointment(UUID appointmentId) {
        // Explicitly cancel pending tasks in DB
        // The worker will check this status and skip processing
        List<ScheduledSmsTaskEntity> pendingTasks = scheduledSmsTaskRepository.findAll().stream()
                .filter(t -> t.getAppointment().getId().equals(appointmentId) && t.getStatus() == SmsTaskStatus.PENDING)
                .toList();
        
        for (ScheduledSmsTaskEntity task : pendingTasks) {
            task.setStatus(SmsTaskStatus.CANCELLED);
            scheduledSmsTaskRepository.save(task);
        }
        
        log.info("Cancelled {} pending reminders for appointment {}", pendingTasks.size(), appointmentId);
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "sms-logs", key = "#task.businessId"),
        @CacheEvict(value = "analytics", allEntries = true)
    })
    public void processScheduledTask(SmsReminderTask task) {
        UUID appointmentId = task.getAppointmentId();
        UUID scheduledTaskId = task.getScheduledTaskId();

        ScheduledSmsTaskEntity scheduledTask = scheduledSmsTaskRepository.findById(scheduledTaskId)
                .orElse(null);

        if (scheduledTask == null) {
            log.warn("Skipping reminder task - ScheduledTask record {} not found", scheduledTaskId);
            return;
        }

        if (scheduledTask.getStatus() != SmsTaskStatus.PENDING) {
            log.info("Skipping task {} - Status is already {}", scheduledTaskId, scheduledTask.getStatus());
            return;
        }

        // Mark as PROCESSING before sending to prevent duplicate sends on retry
        scheduledTask.setStatus(SmsTaskStatus.PROCESSING);
        scheduledSmsTaskRepository.save(scheduledTask);
        
        AppointmentEntity appointment = appointmentRepository.findById(appointmentId)
                .orElse(null);

        if (appointment == null) {
            log.warn("Skipping reminder for appointment {} - Appointment not found (deleted?)", appointmentId);
            scheduledTask.setStatus(SmsTaskStatus.FAILED);
            scheduledSmsTaskRepository.save(scheduledTask);
            return;
        }

        if (appointment.getStatus() == AppointmentStatus.CANCELLED || appointment.getStatus() == AppointmentStatus.NO_SHOW) {
            log.info("Skipping reminder for appointment {} - Appointment status is {}", appointmentId, appointment.getStatus());
            scheduledTask.setStatus(SmsTaskStatus.CANCELLED);
            scheduledSmsTaskRepository.save(scheduledTask);
            return;
        }

        if (!appointment.isRemindersEnabled()) {
             log.info("Skipping reminder for appointment {} - Reminders disabled", appointmentId);
             scheduledTask.setStatus(SmsTaskStatus.SKIPPED);
             scheduledSmsTaskRepository.save(scheduledTask);
             return;
        }

        OffsetDateTime currentTargetTime = appointment.getStartDateTime().minusMinutes(task.getLeadTimeMinutes());
        if (!currentTargetTime.isEqual(task.getScheduledAt())) {
             log.info("Skipping stale reminder for appointment {} - Appointment time changed. Task Scheduled: {}, Current Target: {}", 
                 appointmentId, task.getScheduledAt(), currentTargetTime);
             scheduledTask.setStatus(SmsTaskStatus.SKIPPED);
             scheduledSmsTaskRepository.save(scheduledTask);
             return;
        }
        
        if (appointment.getStartDateTime().isBefore(OffsetDateTime.now(clock))) {
            log.info("Skipping reminder for appointment {} - Appointment already started/passed", appointmentId);
            scheduledTask.setStatus(SmsTaskStatus.SKIPPED);
            scheduledSmsTaskRepository.save(scheduledTask);
            return;
        }

        BusinessEntity business = businessRepository.findById(task.getBusinessId())
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));

        if (!business.hasActiveSubscription()) {
            log.warn("Skipping reminder for appointment {} - Business {} subscription expired", appointmentId, business.getId());
            scheduledTask.setStatus(SmsTaskStatus.FAILED);
            scheduledSmsTaskRepository.save(scheduledTask);
            return;
        }

        // Create the Log (The Receipt) just before sending
        String message = buildReminderMessage(appointment, task.getLeadTimeMinutes());
        SmsLogEntity smsLog = SmsLogEntity.builder()
                .business(business)
                .appointment(appointment)
                .recipientPhone(appointment.getCustomerPhone())
                .messageBody(message)
                .status(SmsStatus.PENDING)
                .providerId("none")
                .build();
        smsLog = smsLogRepository.save(smsLog);

        try {
            sendSms(business, smsLog);
            // Update the Plan status
            scheduledTask.setStatus(smsLog.getStatus() == SmsStatus.DELIVERED ? SmsTaskStatus.COMPLETED : SmsTaskStatus.FAILED);
        } catch (SubscriptionExpiredException e) {
            log.error("Failed to send SMS for appointment {}: {}", appointmentId, e.getMessage());
            
            smsLog.setStatus(SmsStatus.FAILED);
            smsLog.setErrorMessage(e.getMessage());
            smsLogRepository.save(smsLog);
            
            scheduledTask.setStatus(SmsTaskStatus.FAILED);
        } catch (Exception e) {
            log.error("Unexpected error sending SMS for appointment {}", appointmentId, e);
            
            smsLog.setStatus(SmsStatus.FAILED);
            smsLog.setErrorMessage("Unexpected error: " + e.getMessage());
            smsLogRepository.save(smsLog);
            
            scheduledTask.setStatus(SmsTaskStatus.FAILED);
        }
        
        scheduledSmsTaskRepository.save(scheduledTask);
    }

    @Transactional
    @RequiresActiveSubscription
    @Caching(evict = {
        @CacheEvict(value = "sms-logs", key = "#business.id"),
        @CacheEvict(value = "analytics", allEntries = true)
    })
    public SmsLogEntity sendSms(BusinessEntity business, SmsLogEntity smsLog) {
        subscriptionService.consumeSmsCredit(business);

        PhilSmsProvider.SendSmsResult result = philSmsProvider.sendSms(smsLog.getRecipientPhone(), smsLog.getMessageBody());

        smsLog.setStatus(result.success() ? SmsStatus.DELIVERED : SmsStatus.FAILED);
        smsLog.setProviderId(result.success() ? result.providerId() : "none");
        smsLog.setProviderResponse(result.rawResponse());
        smsLog.setErrorMessage(result.errorMessage());

        return smsLogRepository.save(smsLog);
    }

    @Cacheable(value = "sms-logs", key = "#businessId", condition = "#pageable.pageNumber == 0 && #branchId == null && #status == null && #startDate == null && #endDate == null")
    public com.orasa.backend.dto.common.PageResponse<SmsLogResponse> getSmsLogs(
            UUID businessId,
            UUID branchId,
            SmsStatus status,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable) {
        
        OffsetDateTime start = startDate != null 
            ? startDate.atStartOfDay(TimeConfig.PH_ZONE).toOffsetDateTime() 
            : null;
        OffsetDateTime end = endDate != null 
            ? endDate.plusDays(1).atStartOfDay(TimeConfig.PH_ZONE).toOffsetDateTime() 
            : null;

        Page<SmsLogResponse> page = smsLogRepository.searchSmsLogs(
                businessId, branchId, status, start, end, pageable)
                .map(this::mapToResponse);
                
        return com.orasa.backend.dto.common.PageResponse.from(page);
    }

    public PhilSmsProvider.BalanceResult getBalance() {
        return philSmsProvider.getBalance();
    }

    private String buildReminderMessage(AppointmentEntity appointment, Integer leadTime) {
        String template = null;

        // 1. Try to use custom template for extra reminder
        if (leadTime != null && appointment.getAdditionalReminderMinutes() != null && 
            leadTime.equals(appointment.getAdditionalReminderMinutes())) {
            if (appointment.getAdditionalReminderTemplate() != null && !appointment.getAdditionalReminderTemplate().isBlank()) {
                template = appointment.getAdditionalReminderTemplate();
            }
        }

        // 2. Try to use global config if no custom template was used
        if (template == null) {
            List<BusinessReminderConfigEntity> configs = reminderConfigService.getEnabledConfigs(
                    appointment.getBusiness().getId()
            );

            if (leadTime != null && configs != null) {
                for (BusinessReminderConfigEntity config : configs) {
                    if (leadTime.equals(config.getLeadTimeMinutes())) {
                        template = config.getMessageTemplate();
                        break;
                    }
                }
            }

            // Fallback to default
            if (template == null) {
                template = (configs == null || configs.isEmpty())
                        ? "Reminder: Appointment on {date} @ {time} at {businessName} ({branchName}). Please arrive 15 mins early."
                        : configs.get(0).getMessageTemplate();
            }
        }

        // 3. Apply replacements to whichever template was chosen
        ZoneId zoneId = TimeConfig.PH_ZONE;
        LocalDate appointmentDate = appointment.getStartDateTime().atZoneSameInstant(zoneId).toLocalDate();
        LocalDate sendingDate = OffsetDateTime.now(clock).atZoneSameInstant(zoneId).toLocalDate();

        String dateString;
        if (appointmentDate.equals(sendingDate)) {
            dateString = "TODAY";
        } else if (appointmentDate.equals(sendingDate.plusDays(1))) { // Reminder sent today for tomorrow
            dateString = "TOMORROW";
        } else {
            dateString = appointment.getStartDateTime().atZoneSameInstant(zoneId).format(DATE_FORMATTER);
        }

        return template
                .replace("{date}", dateString)
                .replace("{time}", appointment.getStartDateTime().atZoneSameInstant(zoneId).format(TIME_FORMATTER))
                .replace("{businessName}", appointment.getBusiness().getName())
                .replace("{branchName}", appointment.getBranch().getName());
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
