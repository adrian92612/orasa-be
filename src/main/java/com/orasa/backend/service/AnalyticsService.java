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

        long smsDelivered = smsLogRepository.countByBusinessIdAndStatusAndCreatedAtBetween(
                businessId, SmsStatus.DELIVERED, start, end);
        
        long smsFailed = smsLogRepository.countByBusinessIdAndStatusAndCreatedAtBetween(
                businessId, SmsStatus.FAILED, start, end);

        var dailyStats = appointmentRepository.getDailyStats(businessId, start, end);
        var serviceStats = appointmentRepository.getServiceStats(businessId, start, end);
        var statusStats = appointmentRepository.getStatusStats(businessId, start, end);

        // Calculate service percentages
        if (totalAppointments > 0) {
            serviceStats = serviceStats.stream()
                .map(stat -> new com.orasa.backend.dto.analytics.ServiceStatsDTO(
                    stat.serviceName(),
                    stat.count(),
                    java.math.BigDecimal.valueOf(stat.count())
                        .multiply(java.math.BigDecimal.valueOf(100))
                        .divide(java.math.BigDecimal.valueOf(totalAppointments), 1, java.math.RoundingMode.HALF_UP)
                ))
                .toList();
        }

        return new DashboardStats(
                totalAppointments,
                scheduledCount,
                walkInCount,
                cancelledCount,
                noShowCount,
                smsDelivered,
                smsFailed,
                dailyStats,
                serviceStats,
                statusStats
        );
    }
}
