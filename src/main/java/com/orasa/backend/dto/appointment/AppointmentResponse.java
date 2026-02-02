package com.orasa.backend.dto.appointment;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.orasa.backend.common.AppointmentStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentResponse {
  
  private UUID id;
  private UUID businessId;
  private UUID branchId;
  private String branchName;
  private String customerName;
  private String customerPhone;
  private OffsetDateTime startDateTime;
  private OffsetDateTime endDateTime;
  private String notes;
  private AppointmentStatus status;
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;
}
