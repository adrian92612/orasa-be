package com.orasa.backend.dto.analytics;

public record WeekdayStatsDTO(
    int dayOfWeek,
    long count
) {}
