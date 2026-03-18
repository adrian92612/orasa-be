package com.orasa.backend.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.orasa.backend.domain.AppointmentEntity;
import com.orasa.backend.domain.ServiceEntity;
import com.orasa.backend.dto.appointment.AppointmentResponse;
import com.orasa.backend.repository.ServiceRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AppointmentMapper {

  private final ServiceRepository serviceRepository;

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
        .status(appointment.getStatus())
        .notes(appointment.getNotes())
        .services(serviceInfos)
        .remindersEnabled(appointment.isRemindersEnabled())
        .additionalReminderMinutes(appointment.getAdditionalReminderMinutes())
        .additionalReminderTemplate(appointment.getAdditionalReminderTemplate())
        .createdAt(appointment.getCreatedAt())
        .updatedAt(appointment.getUpdatedAt())
        .createdBy(appointment.getCreatedBy())
        .updatedBy(appointment.getUpdatedBy())
        .build();
  }

  public List<ServiceEntity> resolveServices(AppointmentEntity appointment) {
    if (appointment.getServices() == null || appointment.getServices().isEmpty()) {
      return new ArrayList<>();
    }
    
    List<UUID> serviceIds = appointment.getServices().stream()
        .map(ServiceEntity::getId)
        .collect(Collectors.toList());
        
    // ServiceRepository.findAllById handles soft-deleted filtering automatically 
    // due to @SQLRestriction on ServiceEntity
    return serviceRepository.findAllById(serviceIds);
  }
}
