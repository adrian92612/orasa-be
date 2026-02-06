package com.orasa.backend.controller;

import com.orasa.backend.dto.analytics.DashboardStats;
import com.orasa.backend.dto.common.ApiResponse;
import com.orasa.backend.service.AnalyticsService;
import com.orasa.backend.service.BusinessService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsController extends BaseController {

    private final AnalyticsService analyticsService;
    private final BusinessService businessService;

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<DashboardStats>> getDashboardStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal UserDetails userDetails 
            // Note: In a real app we'd extract businessId from token or context.
            // Using a helper method or assuming context is set.
            // For now, I'll fetch the business ID from the logged-in user context or similar.
            // But wait, the previous controllers enforce business scope.
            // Let's look at how other controllers get businessId.
            // StaffController doesn't seem to extract it nicely.
            // I'll assume I need to look it up or pass it if the token has it.
            // Let's assume the user has a businessId in their principal or we look it up.
    ) {
        // Ideally we get businessId from the security context to ensure data isolation.
        // Assuming the current user (Owner) has a businessId.
        // I will use a helper from BusinessService or UserRepository to get the business ID for the current user.
        // For MVP, let's assume I can resolve it.
        // To be safe and follow patterns, I'll check how BusinessController does it.
        // BusinessController relies on Auth.
        
        // I'll implement a helper in BusinessService: getBusinessIdForCurrentUser()
        // Or simply fail if I can't find it.
        
        UUID businessId = businessService.getCurrentUserBusinessId();
        
        DashboardStats stats = analyticsService.getDashboardStats(businessId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }
}
