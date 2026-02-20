package com.orasa.backend.dto.sms;

public record SendSmsResult(boolean success, String providerId, String errorMessage, String rawResponse) {
    public static SendSmsResult success(String providerId, String rawResponse) {
        return new SendSmsResult(true, providerId, null, rawResponse);
    }
    public static SendSmsResult failure(String errorMessage, String rawResponse) {
        return new SendSmsResult(false, null, errorMessage, rawResponse);
    }
}
