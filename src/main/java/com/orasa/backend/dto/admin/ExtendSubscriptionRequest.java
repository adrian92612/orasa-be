package com.orasa.backend.dto.admin;

import jakarta.validation.constraints.Min;

public record ExtendSubscriptionRequest(
    @Min(1) int months
) {}
