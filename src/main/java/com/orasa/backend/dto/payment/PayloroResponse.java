package com.orasa.backend.dto.payment;

public record PayloroResponse(
    boolean success,
    String paymentLink,
    String paymentImage,
    String platOrderNo,
    String errorMessage
) {}
