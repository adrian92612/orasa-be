package com.orasa.backend.dto.appointment;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;

import com.orasa.backend.common.AppointmentStatus;
import com.orasa.backend.common.AppointmentType;

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
  private AppointmentType type;
  private String customerName;
  private String customerPhone;
  private OffsetDateTime startDateTime;
  private OffsetDateTime endDateTime;
  private String notes;
  private AppointmentStatus status;
  private UUID serviceId;
  private String serviceName;
  private List<UUID> selectedReminderIds;
  private Integer additionalReminderMinutes;
  private String additionalReminderTemplate;
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;
}
