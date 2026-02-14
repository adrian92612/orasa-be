package com.orasa.backend.dto.analytics;

import com.orasa.backend.common.AppointmentStatus;

public record StatusStatsDTO(
    AppointmentStatus status,
    Long count
) {}
