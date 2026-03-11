package com.orasa.backend.mapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.hibernate.ObjectNotFoundException;
import org.springframework.stereotype.Component;
import com.orasa.backend.domain.AppointmentEntity;
import com.orasa.backend.domain.BaseEntity;
import com.orasa.backend.domain.ServiceEntity;
import com.orasa.backend.dto.appointment.AppointmentResponse;

@Component
public class AppointmentMapper {

  public AppointmentResponse mapToResponse(AppointmentEntity appointment) {
    List<ServiceEntity> resolvedServices = resolveServices(appointment);
    
    List<AppointmentResponse.ServiceInfo> serviceInfos = resolvedServices.stream()
        .map(service -> AppointmentResponse.ServiceInfo.builder()
            .id(service.getId())
            .name(service.getName())
            .deleted(false)
            .build())
        .collect(Collectors.toList());
    
    return AppointmentResponse.builder()
        .id(appointment.getId())
        .businessId(appointment.getBusiness().getId())
        .branchId(appointment.getBranch().getId())
        .branchName(appointment.getBranch().getName())
        .type(appointment.getType())
        .customerName(appointment.getCustomerName())
        .customerPhone(appointment.getCustomerPhone())
        .startDateTime(appointment.getStartDateTime())
        .endDateTime(appointment.getEndDateTime())
        .status(appointment.getStatus())
        .notes(appointment.getNotes())
        .services(serviceInfos)
        .selectedReminderIds(appointment.getSelectedReminders() != null 
            ? appointment.getSelectedReminders().stream().map(BaseEntity::getId).toList()
            : Collections.emptyList())
        .additionalReminderMinutes(appointment.getAdditionalReminderMinutes())
        .additionalReminderTemplate(appointment.getAdditionalReminderTemplate())
        .createdAt(appointment.getCreatedAt())
        .updatedAt(appointment.getUpdatedAt())
        .build();
  }

  public List<ServiceEntity> resolveServices(AppointmentEntity appointment) {
    List<ServiceEntity> resolved = new ArrayList<>();
    if (appointment.getServices() == null) return resolved;
    for (ServiceEntity service : appointment.getServices()) {
      try {
        service.getId(); // force proxy initialization
        resolved.add(service);
      } catch (ObjectNotFoundException e) {
        // Service was soft-deleted, skip
      }
    }
    return resolved;
  }
}
