package com.orasa.backend.dto.appointment;

import java.time.OffsetDateTime;
import java.util.UUID;

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

  @NotNull(message = "End time is required")
  private OffsetDateTime endDateTime;

  private String notes;

  @NotNull(message = "Is Walk-In checkbox is required")
  @Builder.Default
  private boolean isWalkin = false;
}
