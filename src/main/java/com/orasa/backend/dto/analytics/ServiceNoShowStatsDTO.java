package com.orasa.backend.dto.analytics;

import java.math.BigDecimal;

public record ServiceNoShowStatsDTO(
    String serviceName,
    Long totalAppointments,
    Long noShowCount,
    BigDecimal noShowRate
) {}
