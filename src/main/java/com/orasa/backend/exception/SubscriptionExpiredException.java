package com.orasa.backend.exception;

public class SubscriptionExpiredException extends RuntimeException {
    
    private final String subscriptionStatus;
    
    public SubscriptionExpiredException(String message, String subscriptionStatus) {
        super(message);
        this.subscriptionStatus = subscriptionStatus;
    }
    
    public SubscriptionExpiredException(String message) {
        super(message);
        this.subscriptionStatus = "EXPIRED";
    }
    
    public String getSubscriptionStatus() {
        return subscriptionStatus;
    }
}