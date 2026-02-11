package com.orasa.backend.dto.sms;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateReminderConfigRequest {

    @Positive(message = "Lead time must be positive")
    private Integer leadTimeMinutes;

    private String messageTemplate;

    private Boolean enabled;
}
