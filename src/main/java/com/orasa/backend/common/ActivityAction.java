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
    
    // Branch actions
    BRANCH_CREATED,
    BRANCH_UPDATED,
    
    // Service actions
    SERVICE_CREATED,
    SERVICE_UPDATED,
    SERVICE_DELETED,
    
    // Business settings
    REMINDER_CONFIG_UPDATED,
    
    // Authentication
    USER_LOGIN,
    USER_LOGOUT
}
