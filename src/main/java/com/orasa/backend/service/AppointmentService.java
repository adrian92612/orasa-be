package com.orasa.backend.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import com.orasa.backend.common.AppointmentStatus;
import com.orasa.backend.common.AppointmentType;
import com.orasa.backend.common.UserRole;
import com.orasa.backend.domain.AppointmentEntity;
import com.orasa.backend.domain.BaseEntity;
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
import com.orasa.backend.repository.ServiceRepository;
import com.orasa.backend.repository.BusinessReminderConfigRepository;
import com.orasa.backend.domain.ServiceEntity;
import com.orasa.backend.domain.BusinessReminderConfigEntity;
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
  private final ServiceRepository serviceRepository;
  private final BusinessReminderConfigRepository reminderConfigRepository;
  private final Clock clock;

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

    if (!request.getIsWalkin() && request.getStartDateTime().isBefore(OffsetDateTime.now(clock))) {
      throw new InvalidAppointmentException("Appointment time must be in the future");
    }

    if (request.getAdditionalReminderMinutes() != null && request.getAdditionalReminderMinutes() < 0) {
        throw new InvalidAppointmentException("Additional reminder minutes cannot be negative");
    }
    
    Integer reminderMinutes = (request.getAdditionalReminderMinutes() != null && request.getAdditionalReminderMinutes() == 0) 
        ? null 
        : request.getAdditionalReminderMinutes();
        
    AppointmentEntity.AppointmentEntityBuilder builder = AppointmentEntity.builder()
        .business(business)
        .branch(branch)
        .customerName(request.getCustomerName())
        .customerPhone(request.getCustomerPhone())
        .startDateTime(request.getStartDateTime())
        .notes(request.getNotes())
        .remindersEnabled(true)
        .status(AppointmentStatus.PENDING)
        .type(request.getIsWalkin() ? AppointmentType.WALK_IN : AppointmentType.SCHEDULED)
        .additionalReminderMinutes(reminderMinutes);

    if (request.getServiceId() != null) {
      ServiceEntity serviceEntity = serviceRepository.findById(request.getServiceId())
          .orElseThrow(() -> new ResourceNotFoundException("Service not found"));
      builder.service(serviceEntity);
      
      if (request.getEndDateTime() == null) {
        builder.endDateTime(request.getStartDateTime().plusMinutes(serviceEntity.getDurationMinutes()));
      } else {
        builder.endDateTime(request.getEndDateTime());
      }
    } else {
      if (request.getEndDateTime() == null) {
        throw new InvalidAppointmentException("End time or Service is required");
      }
      builder.endDateTime(request.getEndDateTime());
    }

    if (request.getSelectedReminderIds() != null && !request.getSelectedReminderIds().isEmpty()) {
      List<BusinessReminderConfigEntity> selectedReminders = 
          reminderConfigRepository.findAllById(request.getSelectedReminderIds());
      builder.selectedReminders(new java.util.HashSet<>(selectedReminders));
    }

    AppointmentEntity appointment = builder.build();

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

    // Validate access
    validateBranchAccess(user, appointment.getBranch());

    // Capture the before state for logging
    String beforeCustomerName = appointment.getCustomerName();
    String beforeCustomerPhone = appointment.getCustomerPhone();
    OffsetDateTime beforeStartDateTime = appointment.getStartDateTime();
    OffsetDateTime beforeEndDateTime = appointment.getEndDateTime();
    String beforeNotes = appointment.getNotes();
    AppointmentStatus beforeStatus = appointment.getStatus();
    ServiceEntity beforeService = appointment.getService();
    boolean isOriginallyWalkin = appointment.getType() == AppointmentType.WALK_IN;
    
    // CAPTURE REMINDER STATE BEFORE UPDATES
    Set<UUID> beforeReminderIds = appointment.getSelectedReminders() != null 
        ? appointment.getSelectedReminders().stream()
            .map(BaseEntity::getId)
            .collect(java.util.stream.Collectors.toSet())
        : new java.util.HashSet<>();

    // Validate: Type matches
    if (request.getIsWalkin() != null && request.getIsWalkin() != isOriginallyWalkin) {
        throw new InvalidAppointmentException("Cannot change appointment type after creation");
    }
    
    // Validate additionalReminderMinutes >= 0 (allow 0 to clear)
    if (request.getAdditionalReminderMinutes() != null && request.getAdditionalReminderMinutes() < 0) {
        throw new InvalidAppointmentException("Additional reminder minutes cannot be negative");
    }

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

    // TRACK IF START TIME CHANGED
    boolean startTimeChanged = false;
    if (request.getStartDateTime() != null && !request.getStartDateTime().equals(appointment.getStartDateTime())) {
      if (request.getStartDateTime().isBefore(OffsetDateTime.now(clock))) {
        throw new InvalidAppointmentException("Start time must be in the future");
      }
      changes.add(FieldChange.builder()
          .field("Start Time")
          .before(formatDateTime(beforeStartDateTime))
          .after(formatDateTime(request.getStartDateTime()))
          .build());
      appointment.setStartDateTime(request.getStartDateTime());
      startTimeChanged = true;
    }

    if (request.getEndDateTime() != null && !request.getEndDateTime().equals(appointment.getEndDateTime())) {
      if (request.getEndDateTime().isBefore(OffsetDateTime.now(clock))) {
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

    // TRACK IF ADDITIONAL REMINDER MINUTES CHANGED
    boolean additionalReminderChanged = false;
    // Map 0 to null (clearing the reminder)
    Integer newReminderMinutes = (request.getAdditionalReminderMinutes() != null && request.getAdditionalReminderMinutes() == 0) 
        ? null 
        : request.getAdditionalReminderMinutes();

    // Check if changed
    // If request has a value (including 0 -> null), compare with current.
    // If request is null, we ignore (standard PATCH behavior).
    if (request.getAdditionalReminderMinutes() != null) {
         boolean currentIsNull = appointment.getAdditionalReminderMinutes() == null;
         boolean newIsNull = newReminderMinutes == null;
         boolean changed = false;

         if (currentIsNull && !newIsNull) changed = true;
         else if (!currentIsNull && newIsNull) changed = true;
         else if (!currentIsNull && !newIsNull && !appointment.getAdditionalReminderMinutes().equals(newReminderMinutes)) changed = true;

         if (changed) {
            changes.add(FieldChange.builder()
                .field("Reminder Lead Time")
                .before(appointment.getAdditionalReminderMinutes() != null ? appointment.getAdditionalReminderMinutes().toString() : "(default)")
                .after(newReminderMinutes != null ? newReminderMinutes.toString() : "(removed)")
                .build());
            appointment.setAdditionalReminderMinutes(newReminderMinutes);
            additionalReminderChanged = true;
         }
    }

    if (request.getServiceId() != null && (appointment.getService() == null || !request.getServiceId().equals(appointment.getService().getId()))) {
      ServiceEntity serviceEntity = serviceRepository.findById(request.getServiceId())
          .orElseThrow(() -> new ResourceNotFoundException("Service not found"));
      changes.add(FieldChange.builder()
          .field("Service")
          .before(beforeService != null ? beforeService.getName() : "(none)")
          .after(serviceEntity.getName())
          .build());
      appointment.setService(serviceEntity);
      
      // Auto-recalculate end time if service changed and end time wasn't explicitly changed in this request
      if (request.getEndDateTime() == null || request.getEndDateTime().equals(beforeEndDateTime)) {
          OffsetDateTime newEnd = appointment.getStartDateTime().plusMinutes(serviceEntity.getDurationMinutes());
          if (!newEnd.equals(appointment.getEndDateTime())) {
              appointment.setEndDateTime(newEnd);
              changes.add(FieldChange.builder()
                  .field("End Time (Calculated)")
                  .before(formatDateTime(beforeEndDateTime))
                  .after(formatDateTime(newEnd))
                  .build());
          }
      }
    }

    // TRACK IF SELECTED REMINDERS CHANGED
    boolean selectedRemindersChanged = false;
    if (request.getSelectedReminderIds() != null) {
      Set<UUID> newIds = new java.util.HashSet<>(request.getSelectedReminderIds());
      
      if (!beforeReminderIds.equals(newIds)) {
        List<BusinessReminderConfigEntity> selectedReminders = 
            reminderConfigRepository.findAllById(request.getSelectedReminderIds());
        appointment.setSelectedReminders(new java.util.HashSet<>(selectedReminders));
        changes.add(FieldChange.builder()
            .field("Reminders")
            .before(String.valueOf(beforeReminderIds.size()) + " selected")
            .after(String.valueOf(newIds.size()) + " selected")
            .build());
        selectedRemindersChanged = true;
      }
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
    
    // IMPROVED REMINDER RESCHEDULING LOGIC
    boolean statusCancelledOrCompleted = saved.getStatus() == AppointmentStatus.CANCELLED || 
                                         saved.getStatus() == AppointmentStatus.COMPLETED;
    boolean reminderSettingsChanged = startTimeChanged || 
                                     selectedRemindersChanged || 
                                     additionalReminderChanged;
    
    // 1. If appointment is Cancelled or Completed -> Cancel all pending reminders
    if (statusCancelledOrCompleted) {
        smsService.cancelRemindersForAppointment(saved.getId());
    }
    // 2. If still ACTIVE and reminder-related fields changed -> Reschedule
    else if (reminderSettingsChanged && 
             (saved.getStatus() == AppointmentStatus.PENDING || 
              saved.getStatus() == AppointmentStatus.CONFIRMED)) {
        smsService.cancelRemindersForAppointment(saved.getId());
        smsService.scheduleRemindersForAppointment(saved);
    }

    return new UpdateResult(mapToResponse(saved), true);
  }

  @Transactional
  public AppointmentResponse updateAppointmentStatus(UUID userId, UUID id, AppointmentStatus newStatus) {
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    AppointmentEntity appointment = appointmentRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

    // Validate access
    validateBranchAccess(user, appointment.getBranch());

    AppointmentStatus beforeStatus = appointment.getStatus();
    
    // If no change, return immediately
    if (beforeStatus == newStatus) {
        return mapToResponse(appointment);
    }

    // Update status
    appointment.setStatus(newStatus);
    AppointmentEntity saved = appointmentRepository.save(appointment);

    // Log the status change
    activityLogService.logAppointmentStatusChanged(user, saved, beforeStatus.name(), newStatus.name());

    // Handle reminders
    boolean isNewStatusTerminal = newStatus == AppointmentStatus.CANCELLED || newStatus == AppointmentStatus.COMPLETED;
    boolean wasTerminal = beforeStatus == AppointmentStatus.CANCELLED || beforeStatus == AppointmentStatus.COMPLETED;
    
    if (isNewStatusTerminal) {
        // Cancel all pending reminders
        smsService.cancelRemindersForAppointment(saved.getId());
    } else if (wasTerminal) {
        // Reactivating a terminal appointment -> Schedule reminders if applicable
        // Only if not walk-in
        if (saved.getType() != AppointmentType.WALK_IN) {
             try {
                smsService.scheduleRemindersForAppointment(saved);
            } catch (Exception e) {
                System.err.println("Failed to reschedule reminders on status update: " + e.getMessage());
            }
        }
    }

    return mapToResponse(saved);
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
      AppointmentStatus status,
      AppointmentType type,
      LocalDate startDate,
      LocalDate endDate,
      Pageable pageable) {
    
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    
    BranchEntity branch = branchRepository.findById(branchId)
        .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));

    validateBranchAccess(user, branch);

    ZoneId zoneId = ZoneId.of("Asia/Manila");
    OffsetDateTime start = startDate != null ? startDate.atStartOfDay(zoneId).toOffsetDateTime() : MIN_DATE;
    OffsetDateTime end = endDate != null ? endDate.plusDays(1).atStartOfDay(zoneId).toOffsetDateTime() : MAX_DATE;

    return appointmentRepository.searchAppointments(branchId, search, status, type, start, end, pageable)
        .map(this::mapToResponse);
  }

  public Page<AppointmentResponse> searchAppointmentsByBusiness(
      UUID userId,
      UUID businessId,
      String search,
      AppointmentStatus status,
      AppointmentType type,
      LocalDate startDate,
      LocalDate endDate,
      Pageable pageable) {
    
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    
    // Check if user is OWNER and owns this business
    if (user.getRole() != UserRole.OWNER || !user.getBusiness().getId().equals(businessId)) {
        throw new ForbiddenException("You do not have permission to search appointments for this business");
    }

    ZoneId zoneId = ZoneId.of("Asia/Manila");
    OffsetDateTime start = startDate != null ? startDate.atStartOfDay(zoneId).toOffsetDateTime() : MIN_DATE;
    OffsetDateTime end = endDate != null ? endDate.plusDays(1).atStartOfDay(zoneId).toOffsetDateTime() : MAX_DATE;

    return appointmentRepository.searchAppointmentsByBusiness(businessId, search, status, type, start, end, pageable)
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

    // Validate ownership and role
    if (user.getRole() != UserRole.OWNER) {
        throw new ForbiddenException("Only business owners can delete appointments");
    }
    if (!appointment.getBusiness().getId().equals(user.getBusiness().getId())) {
        throw new ForbiddenException("You can only delete appointments for your own business");
    }

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
        .type(appointment.getType())
        .customerName(appointment.getCustomerName())
        .customerPhone(appointment.getCustomerPhone())
        .startDateTime(appointment.getStartDateTime())
        .endDateTime(appointment.getEndDateTime())
        .status(appointment.getStatus())
        .notes(appointment.getNotes())
        .serviceId(appointment.getService() != null ? appointment.getService().getId() : null)
        .serviceName(appointment.getService() != null ? appointment.getService().getName() : null)
        .selectedReminderIds(appointment.getSelectedReminders() != null 
            ? appointment.getSelectedReminders().stream().map(com.orasa.backend.domain.BaseEntity::getId).toList()
            : java.util.Collections.emptyList())
        .additionalReminderMinutes(appointment.getAdditionalReminderMinutes())
        .createdAt(appointment.getCreatedAt())
        .updatedAt(appointment.getUpdatedAt())
        .build();
  }

  private static final ZoneId MANILA_ZONE = ZoneId.of("Asia/Manila");
  private static final DateTimeFormatter DATE_TIME_FORMATTER = 
      DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");
  
  // Safe bounds for PostgreSQL timestamptz
  private static final OffsetDateTime MIN_DATE = OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime MAX_DATE = OffsetDateTime.of(3000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

  private String formatDateTime(OffsetDateTime dateTime) {
    if (dateTime == null) return "(not set)";
    return dateTime.atZoneSameInstant(MANILA_ZONE).format(DATE_TIME_FORMATTER);
  }
}

