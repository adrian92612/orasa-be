package com.orasa.backend.dto.admin;

import jakarta.validation.constraints.Min;

public record AddCreditsRequest(
    @Min(1) int credits
) {}
