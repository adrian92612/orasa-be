package com.orasa.backend.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orasa.backend.dto.common.ApiResponse;
import com.orasa.backend.dto.common.PageResponse;
import com.orasa.backend.dto.sms.SmsLogResponse;

import com.orasa.backend.security.AuthenticatedUser;
import com.orasa.backend.service.sms.PhilSmsProvider;
import com.orasa.backend.service.sms.SmsService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/sms")
@RequiredArgsConstructor
public class SmsController extends BaseController {

    private final SmsService smsService;

    @GetMapping("/logs")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<PageResponse<SmsLogResponse>>> getSmsLogs(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        validateBusinessExists(authenticatedUser);

        Page<SmsLogResponse> logs = smsService.getSmsLogs(authenticatedUser.businessId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(logs)));
    }

    @GetMapping("/balance")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<SmsBalanceResponse>> getBalance(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        validateBusinessExists(authenticatedUser);

        PhilSmsProvider.BalanceResult result = smsService.getBalance();
        
        SmsBalanceResponse response = new SmsBalanceResponse(
                result.success(),
                result.remainingCredits(),
                result.errorMessage()
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    public record SmsBalanceResponse(boolean success, int remainingCredits, String errorMessage) {}
}
