package com.orasa.backend.dto.business;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateBusinessRequest(
    @NotBlank
    @Size(max = 35)
    @Pattern(regexp = "^[a-zA-Z0-9 ]*$", message = "Only alphanumeric characters and spaces are allowed")
    String name
) {}
