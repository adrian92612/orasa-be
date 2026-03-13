package com.orasa.backend.service.sms;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.orasa.backend.config.TimeConfig;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;

import com.orasa.backend.common.AppointmentType;
import com.orasa.backend.common.SmsTaskStatus;
import com.orasa.backend.domain.AppointmentEntity;
import com.orasa.backend.domain.BranchEntity;
import com.orasa.backend.domain.BusinessEntity;
import com.orasa.backend.domain.ScheduledSmsTaskEntity;
import com.orasa.backend.dto.sms.SmsReminderTask;
import com.orasa.backend.repository.AppointmentRepository;
import com.orasa.backend.repository.ScheduledSmsTaskRepository;
import com.orasa.backend.repository.SmsLogRepository;
import com.orasa.backend.service.CacheService;
import com.orasa.backend.service.ReminderConfigService;
import com.orasa.backend.service.SubscriptionService;

@ExtendWith(MockitoExtension.class)
class SmsServiceTest {

    @Mock
    private PhilSmsProvider philSmsProvider;
    @Mock
    private SmsLogRepository smsLogRepository;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private ReminderConfigService reminderConfigService;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private ScheduledSmsTaskRepository scheduledSmsTaskRepository;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private Clock clock;
    @Mock
    private CacheService cacheService;
    @Mock
    private SmsTaskHelper smsTaskHelper;

    @Mock
    private RBlockingQueue<SmsReminderTask> blockingQueue;
    @SuppressWarnings("deprecation")
    @Mock
    private RDelayedQueue<SmsReminderTask> delayedQueue;

    @InjectMocks
    private SmsService smsService;

    @Captor
    private ArgumentCaptor<ScheduledSmsTaskEntity> scheduledTaskCaptor;
    
    @Captor
    private ArgumentCaptor<SmsReminderTask> taskCaptor;

    private BusinessEntity business;
    private BranchEntity branch;
    private AppointmentEntity appointment;
    private final ZoneId zoneId = TimeConfig.PH_ZONE;
    private final OffsetDateTime now = OffsetDateTime.now(zoneId);

    @BeforeEach
    void setUp() {
        business = BusinessEntity.builder()
                .name("Test Biz")
                .build();
        business.setId(UUID.randomUUID());

        branch = BranchEntity.builder()
                .business(business)
                .name("Branch A")
                .build();
        branch.setId(UUID.randomUUID());

        appointment = AppointmentEntity.builder()
                .business(business)
                .branch(branch)
                .customerName("John Doe")
                .customerPhone("639123456789")
                .remindersEnabled(true)
                .type(AppointmentType.SCHEDULED)
                .startDateTime(now.plusHours(24))
                .build();
        appointment.setId(UUID.randomUUID());
    }

    @Test
    void scheduleRemindersForAppointment_disabledReminders_shouldDoNothing() {
        appointment.setRemindersEnabled(false);
        smsService.scheduleRemindersForAppointment(appointment);
        verify(redissonClient, never()).getBlockingQueue(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void scheduleRemindersForAppointment_walkIn_shouldDoNothing() {
        appointment.setType(AppointmentType.WALK_IN);
        smsService.scheduleRemindersForAppointment(appointment);
        verify(redissonClient, never()).getBlockingQueue(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void scheduleRemindersForAppointment_withAdditionalReminder_shouldSchedule() {
        appointment.setAdditionalReminderMinutes(60);
        
        when(clock.instant()).thenReturn(now.toInstant());
        when(clock.getZone()).thenReturn(zoneId);
        
        org.mockito.Mockito.doReturn(blockingQueue).when(redissonClient).getBlockingQueue(org.mockito.ArgumentMatchers.anyString());
        when(redissonClient.getDelayedQueue(blockingQueue)).thenReturn(delayedQueue);
        
        when(scheduledSmsTaskRepository.save(any(ScheduledSmsTaskEntity.class)))
                .thenAnswer(i -> {
                    ScheduledSmsTaskEntity e = i.getArgument(0);
                    e.setId(UUID.randomUUID());
                    return e;
                });

        smsService.scheduleRemindersForAppointment(appointment);

        verify(scheduledSmsTaskRepository).save(scheduledTaskCaptor.capture());
        ScheduledSmsTaskEntity savedTask = scheduledTaskCaptor.getValue();
        assertEquals(60, savedTask.getLeadTimeMinutes());
        assertEquals(SmsTaskStatus.PENDING, savedTask.getStatus());
        
        verify(delayedQueue).offer(taskCaptor.capture(), anyLong(), eq(TimeUnit.MILLISECONDS));
        SmsReminderTask queuedTask = taskCaptor.getValue();
        assertEquals(60, queuedTask.getLeadTimeMinutes());
        assertEquals(appointment.getId(), queuedTask.getAppointmentId());
    }

    @Test
    void cancelRemindersForAppointment_shouldCancelPending() {
        UUID appId = appointment.getId();
        ScheduledSmsTaskEntity pendingTask = ScheduledSmsTaskEntity.builder()
            .appointment(appointment)
            .status(SmsTaskStatus.PENDING)
            .build();
        pendingTask.setId(UUID.randomUUID());
            
        when(scheduledSmsTaskRepository.findByAppointmentIdAndStatus(appId, SmsTaskStatus.PENDING))
            .thenReturn(List.of(pendingTask));
        
        smsService.cancelRemindersForAppointment(appId);
        
        assertEquals(SmsTaskStatus.CANCELLED, pendingTask.getStatus());
        verify(scheduledSmsTaskRepository).saveAll(List.of(pendingTask));
    }
}
