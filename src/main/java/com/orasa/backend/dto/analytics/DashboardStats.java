package com.orasa.backend.dto.analytics;

public record DashboardStats(
    long totalAppointments,
    long scheduledCount,
    long walkInCount,
    long cancelledCount,
    long noShowCount,
    long smsSent,
    long smsFailed
) {}
