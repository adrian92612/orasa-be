package com.orasa.backend.worker;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.orasa.backend.domain.ScheduledSmsTaskEntity;
import com.orasa.backend.dto.sms.SmsReminderTask;
import com.orasa.backend.repository.ScheduledSmsTaskRepository;
import com.orasa.backend.service.sms.SmsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class SmsRecoveryScheduler {

    private final ScheduledSmsTaskRepository scheduledSmsTaskRepository;
    private final SmsService smsService;
    private final Clock clock;

    @Scheduled(fixedRate = 300_000) // Every 5 minutes
    public void recoverOverdueTasks() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<ScheduledSmsTaskEntity> overdueTasks = scheduledSmsTaskRepository.findOverduePendingTasks(now);

        if (overdueTasks.isEmpty()) {
            return;
        }

        log.info("Recovery scheduler found {} overdue PENDING tasks", overdueTasks.size());

        for (ScheduledSmsTaskEntity scheduledTask : overdueTasks) {
            try {
                SmsReminderTask task = SmsReminderTask.builder()
                        .appointmentId(scheduledTask.getAppointment().getId())
                        .businessId(scheduledTask.getBusiness().getId())
                        .scheduledTaskId(scheduledTask.getId())
                        .leadTimeMinutes(scheduledTask.getLeadTimeMinutes())
                        .scheduledAt(scheduledTask.getScheduledAt())
                        .build();

                smsService.processScheduledTask(task);
            } catch (Exception e) {
                log.error("Recovery failed for task {}", scheduledTask.getId(), e);
            }
        }
    }
}
