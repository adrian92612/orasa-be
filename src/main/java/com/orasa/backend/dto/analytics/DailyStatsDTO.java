package com.orasa.backend.dto.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyStatsDTO(
    LocalDate date,
    Long totalAppointments,
    Long completedAppointments,
    BigDecimal estimatedRevenue
) {}
