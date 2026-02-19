package com.orasa.backend.controller;


import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orasa.backend.dto.common.ApiResponse;
import com.orasa.backend.security.AuthenticatedUser;
import com.orasa.backend.service.payment.PaymentService;
import com.orasa.backend.service.payment.PayloroService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController extends BaseController {

    private final PaymentService paymentService;

    @PostMapping("/subscription")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<PayloroService.PayloroResponse>> createSubscriptionPayment(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateSubscriptionPaymentRequest request
    ) {
        System.out.println("[PAYMENT] Processing subscription renewal for User: " + user.userId() + " (" + request.getMonths() + " months)");
        validateBusinessExists(user);
        PayloroService.PayloroResponse response = paymentService.createSubscriptionPayment(
            user.businessId(), 
            request.getMonths(),
            "gcash-qr"
        );
        
        if (response.success()) {
            return ResponseEntity.ok(ApiResponse.success("Payment initiated successfully", response));
        } else {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to initiate payment: " + response.errorMessage()));
        }
    }

    @PostMapping("/credits")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<PayloroService.PayloroResponse>> createCreditsPayment(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateCreditsPaymentRequest request
    ) {
        System.out.println("[PAYMENT] Processing credits top-up for User: " + user.userId() + " (" + request.getCredits() + " credits)");
        validateBusinessExists(user);
        PayloroService.PayloroResponse response = paymentService.createCreditsPayment(user.businessId(), request.getCredits(), request.getMethod());
        
        if (response.success()) {
            return ResponseEntity.ok(ApiResponse.success("Payment initiated successfully", response));
        } else {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to initiate payment: " + response.errorMessage()));
        }
    }

    @Data
    public static class CreateSubscriptionPaymentRequest {
        @NotNull(message = "Number of months is required")
        @Min(value = 1, message = "Minimum subscription is 1 month")
        @Max(value = 12, message = "Maximum subscription is 12 months")
        private Integer months;
    }

    @Data
    public static class CreateCreditsPaymentRequest {
        @NotNull(message = "Credits amount is required")
        @Min(value = 100, message = "Minimum credits top-up is 100")
        private Integer credits;
        
        @NotBlank(message = "Payment method is required")
        private String method; // gcash, grabpay, maya, qrph
    }
}
