package com.orasa.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orasa.backend.dto.common.ApiResponse;
import com.orasa.backend.dto.sms.CreateReminderConfigRequest;
import com.orasa.backend.dto.sms.ReminderConfigResponse;
import com.orasa.backend.dto.sms.UpdateReminderConfigRequest;
import com.orasa.backend.security.AuthenticatedUser;
import com.orasa.backend.service.ReminderConfigService;
import com.orasa.backend.common.RequiresActiveSubscription;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/reminder-configs")
@RequiredArgsConstructor
public class ReminderConfigController extends BaseController {

    private final ReminderConfigService reminderConfigService;

    @PostMapping
    @RequiresActiveSubscription
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<ReminderConfigResponse>> createConfig(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody CreateReminderConfigRequest request
    ) {
        validateBusinessExists(authenticatedUser);

        ReminderConfigResponse config = reminderConfigService.createConfig(
                authenticatedUser.businessId(),
                request
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Reminder configuration created successfully", config));
    }

    @PutMapping("/{configId}")
    @RequiresActiveSubscription
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<ReminderConfigResponse>> updateConfig(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID configId,
            @Valid @RequestBody UpdateReminderConfigRequest request
    ) {
        validateBusinessExists(authenticatedUser);

        ReminderConfigResponse config = reminderConfigService.updateConfig(
                configId,
                authenticatedUser.businessId(),
                request
        );

        return ResponseEntity.ok(ApiResponse.success("Reminder configuration updated successfully", config));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<List<ReminderConfigResponse>>> getMyConfigs(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        validateBusinessExists(authenticatedUser);

        List<ReminderConfigResponse> configs = reminderConfigService.getConfigsByBusiness(
                authenticatedUser.businessId()
        );
        return ResponseEntity.ok(ApiResponse.success(configs));
    }

    @DeleteMapping("/{configId}")
    @RequiresActiveSubscription
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<Void>> deleteConfig(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID configId
    ) {
        validateBusinessExists(authenticatedUser);

        reminderConfigService.deleteConfig(configId, authenticatedUser.businessId());
        return ResponseEntity.ok(ApiResponse.success("Reminder configuration deleted successfully"));
    }
}

