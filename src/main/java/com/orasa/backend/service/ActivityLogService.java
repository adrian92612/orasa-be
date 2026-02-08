package com.orasa.backend.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orasa.backend.common.ActivityAction;
import com.orasa.backend.domain.ActivityLogEntity;
import com.orasa.backend.domain.AppointmentEntity;
import com.orasa.backend.domain.BranchEntity;
import com.orasa.backend.domain.BusinessEntity;
import com.orasa.backend.domain.UserEntity;
import com.orasa.backend.dto.activity.ActivityLogResponse;
import com.orasa.backend.repository.ActivityLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityLogService {
    
    private final ActivityLogRepository activityLogRepository;
    
    private static final ZoneId MANILA_ZONE = ZoneId.of("Asia/Manila");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = 
            DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");
    
    // ==================== LOGGING METHODS ====================
    
    /**
     * General-purpose logging method for any action.
     * Runs synchronously in the same transaction to ensure consistency.
     */
    @Transactional
    public void logAction(UserEntity user, BusinessEntity business, BranchEntity branch, 
                          ActivityAction action, String description) {
        logAction(user, business, branch, action, description, null);
    }
    
    /**
     * General-purpose logging method with details for expandable view.
     */
    @Transactional
    public void logAction(UserEntity user, BusinessEntity business, BranchEntity branch, 
                          ActivityAction action, String description, String details) {
        try {
            ActivityLogEntity activityLog = ActivityLogEntity.builder()
                    .user(user)
                    .business(business)
                    .branch(branch)
                    .action(action.name())
                    .description(description)
                    .details(details)
                    .build();
            
            activityLogRepository.save(activityLog);
            log.debug("Logged action: {} by user {} - {}", action, user.getId(), description);
        } catch (Exception e) {
            log.error("Failed to log activity: {} - {}", action, e.getMessage());
            // Activity logging should never break the main flow
        }
    }
    
    /**
     * Synchronous version (now same as standard logAction)
     */
    @Transactional
    public void logActionSync(UserEntity user, BusinessEntity business, BranchEntity branch, 
                              ActivityAction action, String description) {
        try {
            ActivityLogEntity activityLog = ActivityLogEntity.builder()
                    .user(user)
                    .business(business)
                    .branch(branch)
                    .action(action.name())
                    .description(description)
                    .build();
            
            activityLogRepository.save(activityLog);
            log.debug("Logged action (sync): {} by user {} - {}", action, user.getId(), description);
        } catch (Exception e) {
            log.error("Failed to log activity: {} - {}", action, e.getMessage());
        }
    }
    
    // ==================== APPOINTMENT LOGGING ====================
    
    public void logAppointmentCreated(UserEntity user, AppointmentEntity appointment) {
        String description = String.format(
            "Created appointment for %s on %s",
            appointment.getCustomerName(),
            formatDateTime(appointment.getStartDateTime())
        );
        logAction(user, appointment.getBusiness(), appointment.getBranch(), 
                  ActivityAction.APPOINTMENT_CREATED, description);
    }
    
    public void logAppointmentUpdated(UserEntity user, AppointmentEntity appointment, String details) {
        String description = String.format(
            "Updated appointment for %s",
            appointment.getCustomerName()
        );
        logAction(user, appointment.getBusiness(), appointment.getBranch(), 
                  ActivityAction.APPOINTMENT_UPDATED, description, details);
    }
    
    public void logAppointmentDeleted(UserEntity user, AppointmentEntity appointment) {
        String description = String.format(
            "Deleted appointment for %s scheduled on %s",
            appointment.getCustomerName(),
            formatDateTime(appointment.getStartDateTime())
        );
        logAction(user, appointment.getBusiness(), appointment.getBranch(), 
                  ActivityAction.APPOINTMENT_DELETED, description);
    }
    
    public void logAppointmentStatusChanged(UserEntity user, AppointmentEntity appointment, 
                                            String oldStatus, String newStatus) {
        String description = String.format(
            "Changed status for %s: %s → %s",
            appointment.getCustomerName(),
            oldStatus,
            newStatus
        );
        String details = String.format("Status: %s → %s", oldStatus, newStatus);
        logAction(user, appointment.getBusiness(), appointment.getBranch(), 
                  ActivityAction.APPOINTMENT_STATUS_CHANGED, description, details);
    }
    
    // ==================== STAFF LOGGING ====================
    
    public void logStaffCreated(UserEntity actor, BusinessEntity business, String staffName) {
        String description = String.format("Created staff account: %s", staffName);
        logAction(actor, business, null, ActivityAction.STAFF_CREATED, description);
    }
    
    public void logStaffUpdated(UserEntity actor, BusinessEntity business, String staffName, String details) {
        String description = String.format("Updated staff: %s", staffName);
        logAction(actor, business, null, ActivityAction.STAFF_UPDATED, description, details);
    }
    
    public void logStaffPasswordReset(UserEntity actor, BusinessEntity business, String staffName) {
        String description = String.format("Reset password for staff: %s", staffName);
        logAction(actor, business, null, ActivityAction.STAFF_PASSWORD_RESET, description);
    }
    
    public void logStaffDeactivated(UserEntity actor, BusinessEntity business, String staffName) {
        String description = String.format("Deactivated staff account: %s", staffName);
        logAction(actor, business, null, ActivityAction.STAFF_DEACTIVATED, description);
    }

    public void logProfileUpdated(UserEntity actor, BusinessEntity business, String details) {
        String description = "Updated profile details";
        logAction(actor, business, null, ActivityAction.PROFILE_UPDATED, description, details);
    }

    public void logPasswordChanged(UserEntity actor, BusinessEntity business) {
        String description = "Changed password";
        logAction(actor, business, null, ActivityAction.PASSWORD_CHANGED, description);
    }
    
    // ==================== BRANCH LOGGING ====================
    
    public void logBranchCreated(UserEntity actor, BusinessEntity business, BranchEntity branch) {
        String description = String.format("Created branch: %s", branch.getName());
        logAction(actor, business, branch, ActivityAction.BRANCH_CREATED, description);
    }
    
    public void logBranchUpdated(UserEntity actor, BusinessEntity business, BranchEntity branch, String changes) {
        String description = String.format("Updated branch %s", branch.getName());
        logAction(actor, business, branch, ActivityAction.BRANCH_UPDATED, description, changes);
    }
    
    public void logBranchDeleted(UserEntity actor, BusinessEntity business, String branchName) {
        String description = String.format("Deleted branch: %s", branchName);
        logAction(actor, business, null, ActivityAction.BRANCH_DELETED, description);
    }
    
    // ==================== SERVICE LOGGING ====================
    
    public void logServiceCreated(UserEntity actor, BusinessEntity business, String serviceName) {
        String description = String.format("Created service: %s", serviceName);
        logAction(actor, business, null, ActivityAction.SERVICE_CREATED, description);
    }
    
    public void logServiceUpdated(UserEntity actor, BusinessEntity business, String serviceName, String details) {
        String description = String.format("Updated service: %s", serviceName);
        logAction(actor, business, null, ActivityAction.SERVICE_UPDATED, description, details);
    }
    
    public void logServiceDeleted(UserEntity actor, BusinessEntity business, String serviceName) {
        String description = String.format("Deleted service: %s", serviceName);
        logAction(actor, business, null, ActivityAction.SERVICE_DELETED, description);
    }
    
    // ==================== SETTINGS LOGGING ====================
    
    public void logReminderConfigUpdated(UserEntity actor, BusinessEntity business, String changes) {
        String description = String.format("Updated reminder configuration. Changes: %s", changes);
        logAction(actor, business, null, ActivityAction.REMINDER_CONFIG_UPDATED, description);
    }

    public void logBusinessUpdated(UserEntity actor, BusinessEntity business, String details) {
        String description = "Updated business settings";
        logAction(actor, business, null, ActivityAction.BUSINESS_UPDATED, description, details);
    }

    public void logBusinessCreated(UserEntity actor, BusinessEntity business) {
        String description = String.format("Created business: %s", business.getName());
        logAction(actor, business, null, ActivityAction.BUSINESS_CREATED, description);
    }
    
    // ==================== AUTH LOGGING ====================
    
    public void logUserLogin(UserEntity user, BusinessEntity business) {
        String description = String.format("User logged in: %s", user.getDisplayName());
        logAction(user, business, null, ActivityAction.USER_LOGIN, description);
    }
    
    public void logUserLogout(UserEntity user, BusinessEntity business) {
        String description = String.format("User logged out: %s", user.getDisplayName());
        logAction(user, business, null, ActivityAction.USER_LOGOUT, description);
    }
    
    // ==================== QUERY METHODS ====================
    
    @Transactional(readOnly = true)
    public Page<ActivityLogResponse> getActivityLogsByBusiness(UUID businessId, Pageable pageable) {
        return activityLogRepository.findByBusinessIdOrderByCreatedAtDesc(businessId, pageable)
                .map(this::mapToResponse);
    }
    
    @Transactional(readOnly = true)
    public Page<ActivityLogResponse> getActivityLogsByBranch(UUID branchId, Pageable pageable) {
        return activityLogRepository.findByBranchIdOrderByCreatedAtDesc(branchId, pageable)
                .map(this::mapToResponse);
    }
    
    @Transactional(readOnly = true)
    public Page<ActivityLogResponse> getActivityLogsByUser(UUID userId, Pageable pageable) {
        return activityLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::mapToResponse);
    }
    
    @Transactional(readOnly = true)
    public Page<ActivityLogResponse> searchActivityLogs(
            UUID businessId,
            UUID branchId,
            String action,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable) {
        
        OffsetDateTime start = startDate != null 
            ? startDate.atStartOfDay(MANILA_ZONE).toOffsetDateTime() 
            : null;
        OffsetDateTime end = endDate != null 
            ? endDate.plusDays(1).atStartOfDay(MANILA_ZONE).toOffsetDateTime() 
            : null;
        
        return activityLogRepository.searchActivityLogs(
                businessId, branchId, action, start, end, pageable)
                .map(this::mapToResponse);
    }
    
    // ==================== HELPER METHODS ====================
    
    private ActivityLogResponse mapToResponse(ActivityLogEntity log) {
        return ActivityLogResponse.builder()
                .id(log.getId())
                .userId(log.getUser().getId())
                .userName(log.getUser().getDisplayName())
                .userRole(log.getUser().getRole().name())
                .businessId(log.getBusiness().getId())
                .branchId(log.getBranch() != null ? log.getBranch().getId() : null)
                .branchName(log.getBranch() != null ? log.getBranch().getName() : null)
                .action(log.getAction())
                .description(log.getDescription())
                .details(log.getDetails())
                .createdAt(log.getCreatedAt())
                .build();
    }
    
    private String formatDateTime(OffsetDateTime dateTime) {
        return dateTime.atZoneSameInstant(MANILA_ZONE).format(DATE_TIME_FORMATTER);
    }
}
