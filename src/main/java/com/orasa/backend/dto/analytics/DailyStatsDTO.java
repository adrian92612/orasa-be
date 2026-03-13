package com.orasa.backend.dto.analytics;

import java.time.LocalDate;

public record DailyStatsDTO(
    LocalDate date,
    Long totalAppointments,
    Long completedAppointments
) {}
