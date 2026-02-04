package com.orasa.backend.dto.service;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignServiceToBranchRequest {

    @NotNull(message = "Service ID is required")
    private UUID serviceId;

    @Positive(message = "Custom price must be positive")
    private BigDecimal customPrice;

    @Builder.Default
    private boolean active = true;
}
