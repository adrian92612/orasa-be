package com.orasa.backend.dto.appointment;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAppointmentRequest {
  @NotNull(message = "Business ID is required")
  private UUID businessId;  

  @NotNull(message = "Branch ID is required")
  private UUID branchId;

  @NotNull(message = "Customer name is required")
  private String customerName;

  @NotNull(message = "Customer phone is required")
  private String customerPhone;
  
  @NotNull(message = "Start time is required")
  private OffsetDateTime startDateTime;

  private OffsetDateTime endDateTime;

  private String notes;

  @NotNull(message = "Is Walk-In checkbox is required")
  @Builder.Default
  private Boolean isWalkin = false;

  private UUID serviceId;

  private List<UUID> selectedReminderIds;

  @Min(value = 0, message = "Additional reminder cannot be negative")
  private Integer additionalReminderMinutes;
}
