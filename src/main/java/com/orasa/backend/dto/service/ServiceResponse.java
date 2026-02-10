package com.orasa.backend.dto.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceResponse {
    private UUID id;
    private UUID businessId;
    private String name;
    private String description;
    private BigDecimal basePrice;
    private Integer durationMinutes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
