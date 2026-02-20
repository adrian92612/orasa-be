package com.orasa.backend.dto.payment;

public record PaymentStatusMessage(
    String merchantOrderNo,
    String status,
    String type
) {}
