package com.orasa.backend.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orasa.backend.dto.business.BusinessResponse;
import com.orasa.backend.dto.common.ApiResponse;
import com.orasa.backend.service.BusinessService;
import com.orasa.backend.service.SubscriptionService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class PlatformAdminController extends BaseController {

    private final BusinessService businessService;
    private final SubscriptionService subscriptionService;

    @GetMapping("/businesses")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<BusinessResponse>>> getAllBusinesses(
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<BusinessResponse> businesses = businessService.getAllBusinesses(query, pageable);
        return ResponseEntity.ok(ApiResponse.success(businesses));
    }

    @PostMapping("/businesses/{businessId}/subscription/extend")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> extendSubscription(
            @PathVariable UUID businessId,
            @Valid @RequestBody ExtendSubscriptionRequest request
    ) {
        subscriptionService.extendSubscription(businessId, request.months());
        return ResponseEntity.ok(ApiResponse.success("Subscription extended successfully"));
    }

    @PostMapping("/businesses/{businessId}/subscription/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> activateSubscription(
            @PathVariable UUID businessId
    ) {
        subscriptionService.activateSubscription(businessId);
        return ResponseEntity.ok(ApiResponse.success("Subscription activated successfully"));
    }

    public record ExtendSubscriptionRequest(
            @Min(1) int months
    ) {}
}
