package com.orasa.backend.dto.sms;

public record SmsBalanceResponse(
    int remainingCredits,
    boolean success,
    String errorMessage
) {}
