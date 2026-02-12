package com.orasa.backend.dto.appointment;

import com.orasa.backend.common.AppointmentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAppointmentStatusRequest {
    @NotNull(message = "Status is required")
    private AppointmentStatus status;
}
