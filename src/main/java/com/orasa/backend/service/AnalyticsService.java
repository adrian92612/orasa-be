package com.orasa.backend.service;

import com.orasa.backend.common.AppointmentStatus;
import com.orasa.backend.common.AppointmentType;
import com.orasa.backend.common.SmsStatus;
import com.orasa.backend.dto.analytics.DashboardStats;
import com.orasa.backend.repository.AppointmentRepository;
import com.orasa.backend.repository.SmsLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsService {

    private final AppointmentRepository appointmentRepository;
    private final SmsLogRepository smsLogRepository;
    private final Clock clock;

    public DashboardStats getDashboardStats(UUID businessId, LocalDate startDate, LocalDate endDate) {
        ZoneId zoneId = clock.getZone();
        OffsetDateTime start = startDate.atStartOfDay(zoneId).toOffsetDateTime();
        OffsetDateTime end = endDate.plusDays(1).atStartOfDay(zoneId).toOffsetDateTime().minusNanos(1);

        long totalAppointments = appointmentRepository.countByBusinessIdAndStartDateTimeBetween(businessId, start, end);
        
        long scheduledCount = appointmentRepository.countByBusinessIdAndTypeAndStartDateTimeBetween(
                businessId, AppointmentType.SCHEDULED, start, end);
        
        long walkInCount = appointmentRepository.countByBusinessIdAndTypeAndStartDateTimeBetween(
                businessId, AppointmentType.WALK_IN, start, end);
        
        long cancelledCount = appointmentRepository.countByBusinessIdAndStatusAndStartDateTimeBetween(
                businessId, AppointmentStatus.CANCELLED, start, end);
        
        long noShowCount = appointmentRepository.countByBusinessIdAndStatusAndStartDateTimeBetween(
                businessId, AppointmentStatus.NO_SHOW, start, end);

        long smsSent = smsLogRepository.countByBusinessIdAndStatusAndCreatedAtBetween(
                businessId, SmsStatus.SENT, start, end);
        
        long smsFailed = smsLogRepository.countByBusinessIdAndStatusAndCreatedAtBetween(
                businessId, SmsStatus.FAILED, start, end);

        return new DashboardStats(
                totalAppointments,
                scheduledCount,
                walkInCount,
                cancelledCount,
                noShowCount,
                smsSent,
                smsFailed
        );
    }
}
