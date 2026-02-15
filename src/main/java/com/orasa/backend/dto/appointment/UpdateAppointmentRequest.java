package com.orasa.backend.dto.appointment;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;

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
public class UpdateAppointmentRequest {

  @NotNull(message = "Customer name is required")
  private String customerName;

  @NotNull(message = "Customer phone is required")
  private String customerPhone;
  
  @NotNull(message = "Start time is required")
  private OffsetDateTime startDateTime;

  @NotNull(message = "End time is required")
  private OffsetDateTime endDateTime;

  private String notes;

  @NotNull(message = "Appointment status is required")
  private AppointmentStatus status;

  private UUID serviceId;

  private List<UUID> selectedReminderIds;

  private Integer additionalReminderMinutes;
  
  private String additionalReminderTemplate;
  
  private Boolean isWalkin;
}
