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
public class BranchServiceResponse {
    private UUID id;
    private UUID branchId;
    private UUID serviceId;
    private String serviceName;
    private String serviceDescription;
    private BigDecimal basePrice;
    private BigDecimal customPrice;
    private BigDecimal effectivePrice;
    private Integer durationMinutes;
    private boolean active;
    private OffsetDateTime createdAt;
}
