package com.orasa.backend.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import com.orasa.backend.common.AppointmentStatus;
import com.orasa.backend.common.UserRole;
import com.orasa.backend.domain.AppointmentEntity;
import com.orasa.backend.domain.BranchEntity;
import com.orasa.backend.domain.BusinessEntity;
import com.orasa.backend.domain.UserEntity;
import com.orasa.backend.dto.activity.FieldChange;
import com.orasa.backend.dto.appointment.AppointmentResponse;
import com.orasa.backend.dto.appointment.CreateAppointmentRequest;
import com.orasa.backend.dto.appointment.UpdateAppointmentRequest;
import com.orasa.backend.dto.appointment.UpdateResult;
import com.orasa.backend.exception.ForbiddenException;
import com.orasa.backend.exception.InvalidAppointmentException;
import com.orasa.backend.exception.ResourceNotFoundException;
import com.orasa.backend.repository.AppointmentRepository;
import com.orasa.backend.repository.BranchRepository;
import com.orasa.backend.repository.BusinessRepository;
import com.orasa.backend.repository.UserRepository;
import com.orasa.backend.service.sms.SmsService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppointmentService {
  private final AppointmentRepository appointmentRepository;
  private final BranchRepository branchRepository;
  private final BusinessRepository businessRepository;
  private final UserRepository userRepository;
  private final ActivityLogService activityLogService;
  private final SmsService smsService;

  @Transactional
  public AppointmentResponse createAppointment(UUID userId, CreateAppointmentRequest request) {
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    BusinessEntity business = businessRepository.findById(request.getBusinessId())
        .orElseThrow(() -> new ResourceNotFoundException("Business not found"));

    BranchEntity branch = branchRepository.findById(request.getBranchId())
        .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));

    if (!branch.getBusiness().getId().equals(request.getBusinessId())) {
      throw new InvalidAppointmentException("Branch does not belong to the specified business");
    }

    // Validate user access to branch
    validateBranchAccess(user, branch);

    if (!request.getIsWalkin() && request.getStartDateTime().isBefore(OffsetDateTime.now())) {
      throw new InvalidAppointmentException("Appointment time must be in the future");
    }

    AppointmentEntity appointment = AppointmentEntity.builder()
        .business(business)
        .branch(branch)
        .customerName(request.getCustomerName())
        .customerPhone(request.getCustomerPhone())
        .startDateTime(request.getStartDateTime())
        .endDateTime(request.getEndDateTime())
        .status(request.getIsWalkin() ? AppointmentStatus.WALK_IN : AppointmentStatus.SCHEDULED)
        .notes(request.getNotes())
        .reminderLeadTimeMinutesOverride(request.getReminderLeadTimeMinutes())
        .build();

    AppointmentEntity saved = appointmentRepository.save(appointment);
    
    // Log the activity asynchronously
    activityLogService.logAppointmentCreated(user, saved);

    // Schedule reminders if not walk-in
    if (!request.getIsWalkin()) {
        try {
            smsService.scheduleRemindersForAppointment(saved);
        } catch (Exception e) {
            // Log but don't fail the appointment creation
            // Future improvement: retry queue
            System.err.println("Failed to schedule reminders: " + e.getMessage());
        }
    }

    return mapToResponse(saved);
  }

  @Transactional
  public UpdateResult updateAppointment(UUID userId, UUID id, UpdateAppointmentRequest request) {
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    AppointmentEntity appointment = appointmentRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

    // Capture the before state for logging
    String beforeCustomerName = appointment.getCustomerName();
    String beforeCustomerPhone = appointment.getCustomerPhone();
    OffsetDateTime beforeStartDateTime = appointment.getStartDateTime();
    OffsetDateTime beforeEndDateTime = appointment.getEndDateTime();
    String beforeNotes = appointment.getNotes();
    AppointmentStatus beforeStatus = appointment.getStatus();

    List<FieldChange> changes = new ArrayList<>();

    if (request.getCustomerName() != null && !request.getCustomerName().equals(appointment.getCustomerName())) {
      changes.add(FieldChange.builder()
          .field("Customer Name")
          .before(beforeCustomerName)
          .after(request.getCustomerName())
          .build());
      appointment.setCustomerName(request.getCustomerName());
    }

    if (request.getCustomerPhone() != null && !request.getCustomerPhone().equals(appointment.getCustomerPhone())) {
      changes.add(FieldChange.builder()
          .field("Phone")
          .before(beforeCustomerPhone)
          .after(request.getCustomerPhone())
          .build());
      appointment.setCustomerPhone(request.getCustomerPhone());
    }

    if (request.getStartDateTime() != null && !request.getStartDateTime().equals(appointment.getStartDateTime())) {
      if (request.getStartDateTime().isBefore(OffsetDateTime.now())) {
        throw new InvalidAppointmentException("Start time must be in the future");
      }
      changes.add(FieldChange.builder()
          .field("Start Time")
          .before(formatDateTime(beforeStartDateTime))
          .after(formatDateTime(request.getStartDateTime()))
          .build());
      appointment.setStartDateTime(request.getStartDateTime());
    }

    if (request.getEndDateTime() != null && !request.getEndDateTime().equals(appointment.getEndDateTime())) {
      if (request.getEndDateTime().isBefore(OffsetDateTime.now())) {
        throw new InvalidAppointmentException("End time must be in the future");
      }
      changes.add(FieldChange.builder()
          .field("End Time")
          .before(formatDateTime(beforeEndDateTime))
          .after(formatDateTime(request.getEndDateTime()))
          .build());
      appointment.setEndDateTime(request.getEndDateTime());
    }

    if (request.getNotes() != null && !request.getNotes().equals(appointment.getNotes())) {
      changes.add(FieldChange.builder()
          .field("Notes")
          .before(beforeNotes != null ? beforeNotes : "")
          .after(request.getNotes())
          .build());
      appointment.setNotes(request.getNotes());
    }

    if (request.getStatus() != null && !request.getStatus().equals(appointment.getStatus())) {
      changes.add(FieldChange.builder()
          .field("Status")
          .before(beforeStatus.name())
          .after(request.getStatus().name())
          .build());
      appointment.setStatus(request.getStatus());
    }

    if (request.getReminderLeadTimeMinutes() != null && !request.getReminderLeadTimeMinutes().equals(appointment.getReminderLeadTimeMinutesOverride())) {
        changes.add(FieldChange.builder()
            .field("Reminder Lead Time")
            .before(appointment.getReminderLeadTimeMinutesOverride() != null ? appointment.getReminderLeadTimeMinutesOverride().toString() : "(default)")
            .after(request.getReminderLeadTimeMinutes().toString())
            .build());
        appointment.setReminderLeadTimeMinutesOverride(request.getReminderLeadTimeMinutes());
    }

    if (changes.isEmpty()) {
      return new UpdateResult(mapToResponse(appointment), false);
    }

    AppointmentEntity saved = appointmentRepository.save(appointment);
    
    // Build structured JSON details
    String details = FieldChange.toJson(changes);
    
    // If status changed, log it as a status change for easier filtering
    if (request.getStatus() != null && !request.getStatus().equals(beforeStatus)) {
      activityLogService.logAppointmentStatusChanged(user, saved, beforeStatus.name(), request.getStatus().name());
    } else {
      activityLogService.logAppointmentUpdated(user, saved, details);
    }
    
    // Handle Reminder Updates
    boolean timeChanged = request.getStartDateTime() != null && !request.getStartDateTime().equals(beforeStartDateTime);
    boolean statusCancelledOrCompleted = saved.getStatus() == AppointmentStatus.CANCELLED || saved.getStatus() == AppointmentStatus.COMPLETED;
    
    // 1. If appointment is Cancelled or Completed -> Cancel all reminders
    if (statusCancelledOrCompleted) {
        smsService.cancelRemindersForAppointment(saved.getId());
    }
    // 2. If it is still SCHEDULED and time changed -> Reschedule
    else if (saved.getStatus() == AppointmentStatus.SCHEDULED && timeChanged) {
        smsService.cancelRemindersForAppointment(saved.getId());
        smsService.scheduleRemindersForAppointment(saved);
    }

    return new UpdateResult(mapToResponse(saved), true);
  }

  public AppointmentResponse getAppointmentById(UUID userId, UUID id) {
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    AppointmentEntity appointment = appointmentRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
    
    validateBranchAccess(user, appointment.getBranch());

    return mapToResponse(appointment);
  }

  public Page<AppointmentResponse> getAppointmentsByBranch(UUID userId, UUID branchId, Pageable pageable) {
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    
    BranchEntity branch = branchRepository.findById(branchId)
        .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));

    validateBranchAccess(user, branch);

    return appointmentRepository.findByBranchId(branchId, pageable).map(this::mapToResponse);
  }

  public Page<AppointmentResponse> getAppointmentsByBusiness(UUID userId, UUID businessId, Pageable pageable) {
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    
    // Check if user is OWNER and owns this business
    if (user.getRole() != UserRole.OWNER || !user.getBusiness().getId().equals(businessId)) {
        throw new ForbiddenException("You do not have permission to access appointments for this business");
    }

    return appointmentRepository.findByBusinessId(businessId, pageable).map(this::mapToResponse);
  }

  public Page<AppointmentResponse> searchAppointments(
      UUID userId,
      UUID branchId,
      String search,
      LocalDate startDate,
      LocalDate endDate,
      Pageable pageable) {
    
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    
    BranchEntity branch = branchRepository.findById(branchId)
        .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));

    validateBranchAccess(user, branch);

    ZoneId zoneId = ZoneId.of("Asia/Manila");
    OffsetDateTime start = startDate.atStartOfDay(zoneId).toOffsetDateTime();
    OffsetDateTime end = endDate.plusDays(1).atStartOfDay(zoneId).toOffsetDateTime();

    return appointmentRepository.searchAppointments(branchId, search, start, end, pageable)
        .map(this::mapToResponse);
  }

  private void validateBranchAccess(UserEntity user, BranchEntity branch) {
    if (user.getRole() == UserRole.OWNER) {
      if (!branch.getBusiness().getId().equals(user.getBusiness().getId())) {
        throw new ForbiddenException("You do not have permission to access this branch");
      }
    } else if (user.getRole() == UserRole.STAFF) {
      boolean hasAccess = user.getBranches().stream()
          .anyMatch(b -> b.getId().equals(branch.getId()));
      if (!hasAccess) {
        throw new ForbiddenException("You are not assigned to this branch");
      }
    } else {
        throw new ForbiddenException("User role not authorized");
    }
  }

  @Transactional
  public void deleteAppointment(UUID userId, UUID id) {
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    AppointmentEntity appointment = appointmentRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

    // Log before deletion (so we have access to appointment data)
    activityLogService.logAppointmentDeleted(user, appointment);

    // Cancel pending reminders
    smsService.cancelRemindersForAppointment(id);

    appointmentRepository.delete(appointment);
  }

  // Helper methods
  private AppointmentResponse mapToResponse(AppointmentEntity appointment) {
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
        .reminderLeadTimeMinutes(appointment.getReminderLeadTimeMinutesOverride())
        .createdAt(appointment.getCreatedAt())
        .updatedAt(appointment.getUpdatedAt())
        .build();
  }

  private static final ZoneId MANILA_ZONE = ZoneId.of("Asia/Manila");
  private static final DateTimeFormatter DATE_TIME_FORMATTER = 
      DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

  private String formatDateTime(OffsetDateTime dateTime) {
    if (dateTime == null) return "(not set)";
    return dateTime.atZoneSameInstant(MANILA_ZONE).format(DATE_TIME_FORMATTER);
  }
}

