package com.orasa.backend.dto.sms;

public record BalanceResult(boolean success, int remainingCredits, String errorMessage) {
    public static BalanceResult success(int credits) {
        return new BalanceResult(true, credits, null);
    }
    public static BalanceResult failure(String errorMessage) {
        return new BalanceResult(false, 0, errorMessage);
    }
}
