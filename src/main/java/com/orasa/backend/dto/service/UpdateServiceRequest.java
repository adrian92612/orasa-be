package com.orasa.backend.dto.service;

import java.math.BigDecimal;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateServiceRequest {

    private String name;

    private String description;

    @Positive(message = "Base price must be positive")
    private BigDecimal basePrice;

    @Positive(message = "Duration must be positive")
    private Integer durationMinutes;

    private Boolean availableGlobally;
}
