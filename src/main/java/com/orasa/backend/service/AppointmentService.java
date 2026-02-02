package com.orasa.backend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orasa.backend.common.AppointmentStatus;
import com.orasa.backend.domain.Appointment;
import com.orasa.backend.domain.Branch;
import com.orasa.backend.domain.Business;
import com.orasa.backend.dto.appointment.AppointmentResponse;
import com.orasa.backend.dto.appointment.CreateAppointmentRequest;
import com.orasa.backend.dto.appointment.UpdateAppointmentRequest;
import com.orasa.backend.dto.appointment.UpdateResult;
import com.orasa.backend.exception.InvalidAppointmentException;
import com.orasa.backend.exception.ResourceNotFoundException;
import com.orasa.backend.repository.AppointmentRepository;
import com.orasa.backend.repository.BranchRepository;
import com.orasa.backend.repository.BusinessRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppointmentService {
  private final AppointmentRepository appointmentRepository;
  private final BranchRepository branchRepository;
  private final BusinessRepository businessRepository;

  @Transactional
  public AppointmentResponse createAppointment(CreateAppointmentRequest request) {
    Business business = businessRepository.findById(request.getBusinessId())
        .orElseThrow(()-> new ResourceNotFoundException("Business not found"));

    Branch branch = branchRepository.findById(request.getBranchId())
        .orElseThrow(()-> new ResourceNotFoundException("Branch not found"));

    if (!branch.getBusiness().getId().equals(request.getBusinessId())) {
      throw new InvalidAppointmentException("Branch does not belong to the specified business");
    }

    if (!request.isWalkin() && request.getStartDateTime().isBefore(OffsetDateTime.now())) {
      throw new InvalidAppointmentException("Appointment time must be in the future");
    }

    Appointment appointment = Appointment.builder()
        .business(business)
        .branch(branch)
        .customerName(request.getCustomerName())
        .customerPhone(request.getCustomerPhone())
        .startDateTime(request.getStartDateTime())
        .endDateTime(request.getEndDateTime())
        .status(request.isWalkin() ? AppointmentStatus.WALK_IN : AppointmentStatus.SCHEDULED)
        .notes(request.getNotes())
        .build();

    Appointment saved = appointmentRepository.save(appointment);
    return mapToResponse(saved);
  }

  @Transactional
  public UpdateResult updateAppointment(UUID id, UpdateAppointmentRequest request) {
    Appointment appointment = appointmentRepository.findById(id)
        .orElseThrow(()-> new ResourceNotFoundException("Appointment not found"));

    boolean hasChanges = false;

    if (request.getCustomerName() != null && !request.getCustomerName().equals(appointment.getCustomerName())) {
      appointment.setCustomerName(request.getCustomerName());
      hasChanges = true;
    }

    if (request.getCustomerPhone() != null && !request.getCustomerPhone().equals(appointment.getCustomerPhone())) {
      appointment.setCustomerPhone(request.getCustomerPhone());
      hasChanges = true;
    }

    if (request.getStartDateTime() != null && !request.getStartDateTime().equals(appointment.getStartDateTime())) {
      if (request.getStartDateTime().isBefore(OffsetDateTime.now())) {
            throw new InvalidAppointmentException("Start time must be in the future");
      }
      appointment.setStartDateTime(request.getStartDateTime());
      hasChanges = true;
    }

    if (request.getEndDateTime() != null && !request.getEndDateTime().equals(appointment.getEndDateTime())) {
      if (request.getEndDateTime().isBefore(OffsetDateTime.now())) {
            throw new InvalidAppointmentException("End time must be in the future");
      }
      appointment.setEndDateTime(request.getEndDateTime());
      hasChanges = true;
    }

    if (request.getNotes() != null && !request.getNotes().equals(appointment.getNotes())) {
      appointment.setNotes(request.getNotes());
      hasChanges = true;
    }

    if (request.getStatus() != null && !request.getStatus().equals(appointment.getStatus())) {
      appointment.setStatus(request.getStatus());
      hasChanges = true;
    }

    if (!hasChanges) {
      return new UpdateResult(mapToResponse(appointment), false);
    }

    Appointment saved = appointmentRepository.save(appointment);
    return new UpdateResult(mapToResponse(saved), true);
  }

  public Page<AppointmentResponse> getAppointmentsByBranch(UUID branchId, Pageable pageable) {
    return appointmentRepository.findByBranchId(branchId, pageable).map(this::mapToResponse);
  }

  public Page<AppointmentResponse> getAppointmentsByBusiness(UUID businessId, Pageable pageable) {
    return appointmentRepository.findByBusinessId(businessId, pageable).map(this::mapToResponse);
  }

  public Page<AppointmentResponse> searchAppointments(
      UUID branchId, 
      String search, 
      LocalDate startDate,
      LocalDate endDate,
      Pageable pageable) {
    ZoneId zoneId = ZoneId.of("Asia/Manila");
    OffsetDateTime start = startDate.atStartOfDay(zoneId).toOffsetDateTime();
    OffsetDateTime end = endDate.plusDays(1).atStartOfDay(zoneId).toOffsetDateTime();

    return appointmentRepository.searchAppointments(branchId, search, start, end, pageable)
      .map(this::mapToResponse);

  }
  
  @Transactional
  public void deleteAppointment(UUID id) {
    Appointment appointment = appointmentRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

    appointmentRepository.delete(appointment);
  }

  // Helper methods
  private AppointmentResponse mapToResponse(Appointment appointment) {
        return AppointmentResponse.builder()
                .id(appointment.getId())
                .businessId(appointment.getBusiness().getId())
                .branchId(appointment.getBranch().getId())
                .branchName(appointment.getBranch().getName())
                .customerName(appointment.getCustomerName())
                .customerPhone(appointment.getCustomerPhone())
                .startDateTime(appointment.getStartDateTime())
                .endDateTime(appointment.getEndDateTime())
                .status(appointment.getStatus())
                .notes(appointment.getNotes())
                .createdAt(appointment.getCreatedAt())
                .updatedAt(appointment.getUpdatedAt())
                .build();
    }
}
