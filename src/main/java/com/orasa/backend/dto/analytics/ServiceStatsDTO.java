package com.orasa.backend.dto.analytics;

import java.math.BigDecimal;

public record ServiceStatsDTO(
    String serviceName,
    Long count,
    BigDecimal percentage
) {}
