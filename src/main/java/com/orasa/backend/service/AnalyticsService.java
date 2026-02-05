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

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsService {

    private final AppointmentRepository appointmentRepository;
    private final SmsLogRepository smsLogRepository;

    public DashboardStats getDashboardStats(UUID businessId, LocalDate startDate, LocalDate endDate) {
        // Convert LocalDate to OffsetDateTime (start of day to end of day)
        // Assuming UTC for now, or system default. Ideally should be based on business timezone but keeping it simple as per requirements.
        // Orasa seems to use OffsetDateTime throughout.
        OffsetDateTime start = startDate.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime end = endDate.atTime(LocalTime.MAX).atOffset(ZoneOffset.UTC);

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
