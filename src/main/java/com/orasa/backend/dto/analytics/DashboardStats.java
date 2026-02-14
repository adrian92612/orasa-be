package com.orasa.backend.dto.analytics;

public record DashboardStats(
    long totalAppointments,
    long scheduledCount,
    long walkInCount,
    long cancelledCount,
    long noShowCount,
    long smsSent,
    long smsFailed,
    java.util.List<DailyStatsDTO> dailyStats,
    java.util.List<ServiceStatsDTO> serviceStats,
    java.util.List<StatusStatsDTO> statusStats
) {}
