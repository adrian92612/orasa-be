package com.orasa.backend.dto.appointment;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UpdateResult {
  private AppointmentResponse appointment;
  private boolean modified;
}
