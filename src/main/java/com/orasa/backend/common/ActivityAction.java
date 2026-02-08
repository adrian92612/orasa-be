package com.orasa.backend.common;

public enum ActivityAction {
    // Appointment actions
    APPOINTMENT_CREATED,
    APPOINTMENT_UPDATED,
    APPOINTMENT_DELETED,
    APPOINTMENT_STATUS_CHANGED,
    
    // Staff actions
    STAFF_CREATED,
    STAFF_UPDATED,
    STAFF_PASSWORD_RESET,
    STAFF_DEACTIVATED,
    
    // Profile actions
    PROFILE_UPDATED,
    PASSWORD_CHANGED,
    
    // Branch actions
    BRANCH_CREATED,
    BRANCH_UPDATED,
    BRANCH_DELETED,
    
    // Service actions
    SERVICE_CREATED,
    SERVICE_UPDATED,
    SERVICE_DELETED,
    
    // Business settings
    REMINDER_CONFIG_UPDATED,
    BUSINESS_UPDATED,
    BUSINESS_CREATED,
    
    // Authentication
    USER_LOGIN,
    USER_LOGOUT
}
