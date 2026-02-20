package com.orasa.backend.dto.payment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateSubscriptionPaymentRequest {
    @NotNull(message = "Number of months is required")
    @Min(value = 1, message = "Minimum subscription is 1 month")
    @Max(value = 12, message = "Maximum subscription is 12 months")
    private Integer months;
}
