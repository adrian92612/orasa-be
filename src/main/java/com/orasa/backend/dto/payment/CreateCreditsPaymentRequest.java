package com.orasa.backend.dto.payment;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateCreditsPaymentRequest {
    @NotNull(message = "Credits amount is required")
    @Min(value = 100, message = "Minimum credits top-up is 100")
    private Integer credits;
    
    @NotBlank(message = "Payment method is required")
    private String method; // gcash, grabpay, maya, qrph
}
