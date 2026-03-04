package com.orasa.backend.dto.payment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;

@Builder
public record PaymentHistoryResponse(
    UUID id,
    String merchantOrderNo,
    String platOrderNo,
    BigDecimal amount,
    String description,
    String method,
    PaymentType type,
    PaymentStatus status,
    OffsetDateTime createdAt
) {}
