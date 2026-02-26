package com.orasa.backend.service;

import com.orasa.backend.common.AppointmentStatus;
import com.orasa.backend.common.AppointmentType;
import com.orasa.backend.common.SmsStatus;
import com.orasa.backend.dto.analytics.DashboardStats;
import com.orasa.backend.dto.analytics.DailyStatsDTO;
import com.orasa.backend.dto.analytics.ServiceStatsDTO;
import com.orasa.backend.dto.analytics.StatusStatsDTO;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import com.orasa.backend.common.CacheName;
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

    @Cacheable(value = CacheName.ANALYTICS, key = "{#businessId, #branchId, #startDate, #endDate}")
    public DashboardStats getDashboardStats(UUID businessId, UUID branchId, LocalDate startDate, LocalDate endDate) {
        ZoneId zoneId = clock.getZone();
        OffsetDateTime start = startDate.atStartOfDay(zoneId).toOffsetDateTime();
        OffsetDateTime end = endDate.plusDays(1).atStartOfDay(zoneId).toOffsetDateTime().minusNanos(1);

        long totalAppointments = appointmentRepository.countByBusinessIdAndBranchIdOptionalAndStartDateTimeBetween(businessId, branchId, start, end);
        
        long scheduledCount = appointmentRepository.countByBusinessIdAndBranchIdOptionalAndTypeAndStartDateTimeBetween(
                businessId, branchId, AppointmentType.SCHEDULED, start, end);
        
        long walkInCount = appointmentRepository.countByBusinessIdAndBranchIdOptionalAndTypeAndStartDateTimeBetween(
                businessId, branchId, AppointmentType.WALK_IN, start, end);
        
        long cancelledCount = appointmentRepository.countByBusinessIdAndBranchIdOptionalAndStatusAndStartDateTimeBetween(
                businessId, branchId, AppointmentStatus.CANCELLED, start, end);
        
        long noShowCount = appointmentRepository.countByBusinessIdAndBranchIdOptionalAndStatusAndStartDateTimeBetween(
                businessId, branchId, AppointmentStatus.NO_SHOW, start, end);

        long smsDelivered = smsLogRepository.countByBusinessIdAndBranchIdOptionalAndStatusAndCreatedAtBetween(
                businessId, branchId, SmsStatus.DELIVERED, start, end);
        
        long smsFailed = smsLogRepository.countByBusinessIdAndBranchIdOptionalAndStatusAndCreatedAtBetween(
                businessId, branchId, SmsStatus.FAILED, start, end);

        List<DailyStatsDTO> dailyStats = appointmentRepository.getDailyStats(businessId, branchId, start, end);
        List<ServiceStatsDTO> serviceStats = appointmentRepository.getServiceStats(businessId, branchId, start, end);
        List<StatusStatsDTO> statusStats = appointmentRepository.getStatusStats(businessId, branchId, start, end);

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

        return DashboardStats.builder()
                .totalAppointments(totalAppointments)
                .scheduledCount(scheduledCount)
                .walkInCount(walkInCount)
                .cancelledCount(cancelledCount)
                .noShowCount(noShowCount)
                .smsDelivered(smsDelivered)
                .smsFailed(smsFailed)
                .dailyStats(dailyStats)
                .serviceStats(serviceStats)
                .statusStats(statusStats)
                .build();
    }
}
