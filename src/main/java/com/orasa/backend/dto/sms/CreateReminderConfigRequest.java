package com.orasa.backend.dto.sms;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReminderConfigRequest {

    @NotNull(message = "Lead time minutes is required")
    @Positive(message = "Lead time must be positive")
    private Integer leadTimeMinutes;

    @NotBlank(message = "Message template is required")
    private String messageTemplate;

    @Builder.Default
    private Boolean enabled = true;
}
