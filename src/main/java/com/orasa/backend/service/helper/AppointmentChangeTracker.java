package com.orasa.backend.service.helper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import com.orasa.backend.domain.AppointmentEntity;
import com.orasa.backend.domain.ServiceEntity;
import com.orasa.backend.dto.activity.FieldChange;
import com.orasa.backend.dto.appointment.UpdateAppointmentRequest;
import com.orasa.backend.util.DateTimeUtils;

import lombok.Builder;
import lombok.Getter;

@Component
public class AppointmentChangeTracker {

  @Getter
  @Builder
  public static class ChangeResult {
    private final List<FieldChange> changes;
    private final boolean startTimeChanged;
    private final boolean additionalReminderChanged;
    private final boolean remindersEnabledChanged;
    private final boolean servicesChanged;

    public boolean hasChanges() {
      return !changes.isEmpty();
    }
  }

  public ChangeResult trackChanges(AppointmentEntity appointment, UpdateAppointmentRequest request, List<ServiceEntity> beforeServices) {
    List<FieldChange> changes = new ArrayList<>();
    boolean startTimeChanged = false;
    boolean additionalReminderChanged = false;
    boolean remindersEnabledChanged = false;
    boolean servicesChanged = false;

    // Customer Name
    if (request.getCustomerName() != null && !request.getCustomerName().equals(appointment.getCustomerName())) {
      changes.add(FieldChange.builder()
          .field("Customer Name")
          .before(appointment.getCustomerName())
          .after(request.getCustomerName())
          .build());
      appointment.setCustomerName(request.getCustomerName());
    }

    // Customer Phone
    if (request.getCustomerPhone() != null && !request.getCustomerPhone().equals(appointment.getCustomerPhone())) {
      changes.add(FieldChange.builder()
          .field("Phone")
          .before(appointment.getCustomerPhone())
          .after(request.getCustomerPhone())
          .build());
      appointment.setCustomerPhone(request.getCustomerPhone());
    }

    // Start Time
    if (request.getStartDateTime() != null && !request.getStartDateTime().isEqual(appointment.getStartDateTime())) {
      changes.add(FieldChange.builder()
          .field("Start Time")
          .before(DateTimeUtils.formatDateTime(appointment.getStartDateTime()))
          .after(DateTimeUtils.formatDateTime(request.getStartDateTime()))
          .build());
      appointment.setStartDateTime(request.getStartDateTime());
      startTimeChanged = true;
    }


    // Notes
    if (request.getNotes() != null && !request.getNotes().equals(appointment.getNotes())) {
      changes.add(FieldChange.builder()
          .field("Notes")
          .before(appointment.getNotes() != null ? appointment.getNotes() : "")
          .after(request.getNotes())
          .build());
      appointment.setNotes(request.getNotes());
    }

    // Status
    if (request.getStatus() != null && !request.getStatus().equals(appointment.getStatus())) {
      changes.add(FieldChange.builder()
          .field("Status")
          .before(appointment.getStatus().name())
          .after(request.getStatus().name())
          .build());
      appointment.setStatus(request.getStatus());
    }

    // Additional Reminder Minutes
    Integer newReminderMinutes = (request.getAdditionalReminderMinutes() != null && request.getAdditionalReminderMinutes() == 0) 
        ? null 
        : request.getAdditionalReminderMinutes();

    if (request.getAdditionalReminderMinutes() != null) {
      boolean currentIsNull = appointment.getAdditionalReminderMinutes() == null;
      boolean newIsNull = newReminderMinutes == null;
      boolean changed = false;

      if (currentIsNull && !newIsNull) changed = true;
      else if (!currentIsNull && newIsNull) changed = true;
      else if (!currentIsNull && !newIsNull && !appointment.getAdditionalReminderMinutes().equals(newReminderMinutes)) changed = true;

      if (changed) {
        changes.add(FieldChange.builder()
            .field("Lead Time")
            .before(appointment.getAdditionalReminderMinutes() != null ? appointment.getAdditionalReminderMinutes().toString() : "(default)")
            .after(newReminderMinutes != null ? newReminderMinutes.toString() : "(removed)")
            .build());
        appointment.setAdditionalReminderMinutes(newReminderMinutes);
        additionalReminderChanged = true;
      }
    }

    // Additional Reminder Template
    if (request.getAdditionalReminderTemplate() != null && !request.getAdditionalReminderTemplate().equals(appointment.getAdditionalReminderTemplate())) {
      changes.add(FieldChange.builder()
          .field("Custom Template")
          .before(appointment.getAdditionalReminderTemplate() != null ? "customized" : "default")
          .after("customized")
          .build());
      appointment.setAdditionalReminderTemplate(request.getAdditionalReminderTemplate());
      additionalReminderChanged = true;
    }

    // Service changes detection (actual update happens in service)
    if (request.getServiceIds() != null) {
      Set<UUID> beforeServiceIds = beforeServices.stream()
          .map(ServiceEntity::getId)
          .collect(Collectors.toSet());
      Set<UUID> newServiceIds = new HashSet<>(request.getServiceIds());
      if (!beforeServiceIds.equals(newServiceIds)) {
        servicesChanged = true;
      }
    }

    // Reminders enabled changes
    if (request.getRemindersEnabled() != null) {
      if (appointment.isRemindersEnabled() != request.getRemindersEnabled()) {
        remindersEnabledChanged = true;
      }
    }

    return ChangeResult.builder()
        .changes(changes)
        .startTimeChanged(startTimeChanged)
        .additionalReminderChanged(additionalReminderChanged)
        .remindersEnabledChanged(remindersEnabledChanged)
        .servicesChanged(servicesChanged)
        .build();
  }
}
