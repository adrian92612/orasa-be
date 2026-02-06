package com.orasa.backend.dto.business;

import jakarta.validation.constraints.NotBlank;

public record UpdateBusinessRequest(
    @NotBlank
    String name
) {}
