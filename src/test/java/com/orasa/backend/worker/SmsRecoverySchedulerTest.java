package com.orasa.backend.worker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orasa.backend.domain.AppointmentEntity;
import com.orasa.backend.domain.BusinessEntity;
import com.orasa.backend.domain.ScheduledSmsTaskEntity;
import com.orasa.backend.dto.sms.SmsReminderTask;
import com.orasa.backend.repository.ScheduledSmsTaskRepository;
import com.orasa.backend.service.sms.SmsService;

@ExtendWith(MockitoExtension.class)
public class SmsRecoverySchedulerTest {

    @Mock
    private ScheduledSmsTaskRepository scheduledSmsTaskRepository;

    @Mock
    private SmsService smsService;

    @Mock
    private Clock clock;

    @InjectMocks
    private SmsRecoveryScheduler smsRecoveryScheduler;

    private OffsetDateTime now;

    @BeforeEach
    void setUp() {
        now = OffsetDateTime.of(2026, 3, 2, 8, 0, 0, 0, ZoneOffset.UTC);
    }

    @Test
    @DisplayName("Should process overdue tasks when found")
    void recoverOverdueTasks_withTasks_success() {
        // Arrange
        when(clock.instant()).thenReturn(now.toInstant());
        when(clock.getZone()).thenReturn(now.getOffset());

        BusinessEntity business = BusinessEntity.builder().build();
        business.setId(UUID.randomUUID());

        AppointmentEntity appointment = AppointmentEntity.builder().build();
        appointment.setId(UUID.randomUUID());

        ScheduledSmsTaskEntity taskEntity = ScheduledSmsTaskEntity.builder()
                .business(business)
                .appointment(appointment)
                .leadTimeMinutes(60)
                .scheduledAt(now.minusMinutes(10))
                .build();
        taskEntity.setId(UUID.randomUUID());

        when(scheduledSmsTaskRepository.findOverduePendingTasks(now)).thenReturn(List.of(taskEntity));

        // Act
        smsRecoveryScheduler.recoverOverdueTasks();

        // Assert
        verify(smsService, times(1)).processScheduledTask(any(SmsReminderTask.class));
    }

    @Test
    @DisplayName("Should do nothing when no overdue tasks exist")
    void recoverOverdueTasks_empty_doesNothing() {
        // Arrange
        when(clock.instant()).thenReturn(now.toInstant());
        when(clock.getZone()).thenReturn(now.getOffset());
        when(scheduledSmsTaskRepository.findOverduePendingTasks(now)).thenReturn(Collections.emptyList());

        // Act
        smsRecoveryScheduler.recoverOverdueTasks();

        // Assert
        verify(smsService, never()).processScheduledTask(any());
    }
}
