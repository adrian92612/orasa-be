package com.orasa.backend.controller;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.orasa.backend.dto.activity.ActivityLogResponse;
import com.orasa.backend.dto.common.ApiResponse;
import com.orasa.backend.service.ActivityLogService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/activity-logs")
@RequiredArgsConstructor
public class ActivityLogController extends BaseController {
    
    private final ActivityLogService activityLogService;
    
    /**
     * Get all activity logs for a business (Owner only)
     * Supports pagination via query params: page, size, sort
     */
    @GetMapping("/business/{businessId}")
    @PreAuthorize("hasRole('OWNER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<ActivityLogResponse>>> getActivityLogsByBusiness(
            @PathVariable UUID businessId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<ActivityLogResponse> logs = activityLogService.getActivityLogsByBusiness(businessId, pageable);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }
    
    /**
     * Get activity logs for a specific branch (Owner only)
     */
    @GetMapping("/branch/{branchId}")
    @PreAuthorize("hasRole('OWNER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<ActivityLogResponse>>> getActivityLogsByBranch(
            @PathVariable UUID branchId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<ActivityLogResponse> logs = activityLogService.getActivityLogsByBranch(branchId, pageable);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }
    
    /**
     * Get activity logs for a specific user (Owner/Admin only)
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('OWNER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<ActivityLogResponse>>> getActivityLogsByUser(
            @PathVariable UUID userId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<ActivityLogResponse> logs = activityLogService.getActivityLogsByUser(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }
    
    /**
     * Search activity logs with filters (Owner only)
     * 
     * @param businessId Required - the business to search within
     * @param branchId Optional - filter by specific branch
     * @param action Optional - filter by action type (e.g., APPOINTMENT_CREATED)
     * @param startDate Optional - filter logs from this date (inclusive)
     * @param endDate Optional - filter logs until this date (inclusive)
     */
    @GetMapping("/business/{businessId}/search")
    @PreAuthorize("hasRole('OWNER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<ActivityLogResponse>>> searchActivityLogs(
            @PathVariable UUID businessId,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<ActivityLogResponse> logs = activityLogService.searchActivityLogs(
                businessId, branchId, action, startDate, endDate, pageable);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }
}
