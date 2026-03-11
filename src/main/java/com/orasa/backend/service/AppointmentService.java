package com.orasa.backend.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.ObjectNotFoundException;


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
import com.orasa.backend.repository.AppointmentSpecification;
import com.orasa.backend.repository.BranchRepository;
import com.orasa.backend.repository.BusinessRepository;
import com.orasa.backend.repository.UserRepository;
import com.orasa.backend.repository.ServiceRepository;
import com.orasa.backend.repository.BusinessReminderConfigRepository;
import com.orasa.backend.domain.ServiceEntity;
import com.orasa.backend.domain.BusinessReminderConfigEntity;
import com.orasa.backend.service.sms.SmsService;
import com.orasa.backend.config.TimeConfig;

import com.orasa.backend.common.CacheName;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
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
  private final CacheService cacheService;

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
        .additionalReminderMinutes(reminderMinutes)
        .additionalReminderTemplate(request.getAdditionalReminderTemplate());

    // Resolve services
    List<UUID> serviceIds = request.getServiceIds();
    if (serviceIds != null && !serviceIds.isEmpty()) {
      List<ServiceEntity> serviceEntities = serviceRepository.findAllById(serviceIds);
      if (serviceEntities.size() != serviceIds.size()) {
        throw new ResourceNotFoundException("One or more services not found");
      }
      builder.services(new HashSet<>(serviceEntities));
      
      if (request.getEndDateTime() == null) {
        int totalDuration = serviceEntities.stream()
            .mapToInt(ServiceEntity::getDurationMinutes)
            .sum();
        builder.endDateTime(request.getStartDateTime().plusMinutes(totalDuration));
      } else {
        builder.endDateTime(request.getEndDateTime());
      }
    } else {
      if (request.getEndDateTime() == null) {
        throw new InvalidAppointmentException("End time or Service is required");
      }
      builder.endDateTime(request.getEndDateTime());
    }

    if (request.getSelectedReminderIds() != null) {
      List<BusinessReminderConfigEntity> selectedReminders = 
          reminderConfigRepository.findAllById(request.getSelectedReminderIds());
      builder.selectedReminders(new HashSet<>(selectedReminders));
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
            log.error("Failed to schedule reminders for appointment {}: {}", saved.getId(), e.getMessage(), e);
        }
    }

    cacheService.evictAll(CacheName.ANALYTICS);
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
    List<ServiceEntity> beforeServices = resolveServices(appointment);
    boolean isOriginallyWalkin = appointment.getType() == AppointmentType.WALK_IN;
    
    // CAPTURE REMINDER STATE BEFORE UPDATES
    Set<UUID> beforeReminderIds = appointment.getSelectedReminders() != null 
        ? appointment.getSelectedReminders().stream()
            .map(BaseEntity::getId)
            .collect(Collectors.toSet())
        : new HashSet<>();

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
    if (request.getStartDateTime() != null && !request.getStartDateTime().isEqual(beforeStartDateTime)) {
      // Only validate future time if the start time is actually being changed
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

    if (request.getEndDateTime() != null && !request.getEndDateTime().isEqual(beforeEndDateTime)) {
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

    // Handle services update
    if (request.getServiceIds() != null) {
      Set<UUID> beforeServiceIds = beforeServices.stream()
          .map(ServiceEntity::getId)
          .collect(Collectors.toSet());
      Set<UUID> newServiceIds = new HashSet<>(request.getServiceIds());
      
      if (!beforeServiceIds.equals(newServiceIds)) {
        List<ServiceEntity> newServices = serviceRepository.findAllById(request.getServiceIds());
        if (newServices.size() != request.getServiceIds().size()) {
          throw new ResourceNotFoundException("One or more services not found");
        }
        
        String beforeServiceNames = beforeServices.isEmpty() ? "(none)" : 
            beforeServices.stream().map(ServiceEntity::getName).collect(Collectors.joining(", "));
        String afterServiceNames = newServices.stream().map(ServiceEntity::getName).collect(Collectors.joining(", "));
        
        changes.add(FieldChange.builder()
            .field("Services")
            .before(beforeServiceNames)
            .after(afterServiceNames)
            .build());
        appointment.setServices(new HashSet<>(newServices));
        
        // Auto-recalculate end time if services changed and end time wasn't explicitly changed
        if (request.getEndDateTime() == null || request.getEndDateTime().equals(beforeEndDateTime)) {
            int totalDuration = newServices.stream()
                .mapToInt(ServiceEntity::getDurationMinutes)
                .sum();
            if (totalDuration > 0) {
              OffsetDateTime newEnd = appointment.getStartDateTime().plusMinutes(totalDuration);
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
      }
    }

    if (request.getAdditionalReminderTemplate() != null && !request.getAdditionalReminderTemplate().equals(appointment.getAdditionalReminderTemplate())) {
        changes.add(FieldChange.builder()
            .field("Custom Reminder Template")
            .before(appointment.getAdditionalReminderTemplate() != null ? "customized" : "default")
            .after("customized")
            .build());
        appointment.setAdditionalReminderTemplate(request.getAdditionalReminderTemplate());
        additionalReminderChanged = true;
    }

    // TRACK IF SELECTED REMINDERS CHANGED
    boolean selectedRemindersChanged = false;
    if (request.getSelectedReminderIds() != null) {
      Set<UUID> newIds = new HashSet<>(request.getSelectedReminderIds());
      
      if (!beforeReminderIds.equals(newIds)) {
        List<BusinessReminderConfigEntity> selectedReminders = 
            reminderConfigRepository.findAllById(request.getSelectedReminderIds());
        appointment.setSelectedReminders(new HashSet<>(selectedReminders));
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

    cacheService.evictAll(CacheName.ANALYTICS);
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
                log.error("Failed to reschedule reminders on status update for appointment {}: {}", saved.getId(), e.getMessage(), e);
            }
        }
    }

    cacheService.evictAll(CacheName.ANALYTICS);
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
    
    if (!user.getBusiness().getId().equals(businessId)) {
        throw new ForbiddenException("You do not have permission to access appointments for this business");
    }

    if (user.getRole() == UserRole.STAFF) {
        List<UUID> branchIds = user.getBranches().stream().map(BranchEntity::getId).toList();
        return appointmentRepository.findByBusinessIdAndBranchIdIn(businessId, branchIds, pageable).map(this::mapToResponse);
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

    OffsetDateTime start = startDate != null ? startDate.atStartOfDay(TimeConfig.PH_ZONE).toOffsetDateTime() : MIN_DATE;
    OffsetDateTime end = endDate != null ? endDate.plusDays(1).atStartOfDay(TimeConfig.PH_ZONE).toOffsetDateTime() : MAX_DATE;

    return appointmentRepository.findAll(
        AppointmentSpecification.buildSearchSpec(branchId, null, null, search, status, type, start, end),
        pageable
    ).map(this::mapToResponse);
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
    
    if (!user.getBusiness().getId().equals(businessId)) {
        throw new ForbiddenException("You do not have permission to search appointments for this business");
    }

    OffsetDateTime start = startDate != null ? startDate.atStartOfDay(TimeConfig.PH_ZONE).toOffsetDateTime() : MIN_DATE;
    OffsetDateTime end = endDate != null ? endDate.plusDays(1).atStartOfDay(TimeConfig.PH_ZONE).toOffsetDateTime() : MAX_DATE;
    
    List<UUID> allowedBranchIds = null;
    if (user.getRole() == UserRole.STAFF) {
        allowedBranchIds = user.getBranches().stream().map(BranchEntity::getId).toList();
    }

    Page<AppointmentEntity> page = appointmentRepository.findAll(
        AppointmentSpecification.buildSearchSpec(null, allowedBranchIds, businessId, search, status, type, start, end),
        pageable
    );
    
    if (!page.getContent().isEmpty()) {
        List<UUID> ids = page.getContent().stream()
            .map(AppointmentEntity::getId)
            .toList();
        appointmentRepository.findAllByIdWithAssociations(ids);
    }
    
    return page.map(this::mapToResponse);
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
    cacheService.evictAll(CacheName.ANALYTICS);
  }

  /**
   * Safely resolves services from a collection, filtering out soft-deleted ones.
   */
  private List<ServiceEntity> resolveServices(AppointmentEntity appointment) {
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

  // Helper methods
  private AppointmentResponse mapToResponse(AppointmentEntity appointment) {
    List<ServiceEntity> resolvedServices = resolveServices(appointment);
    
    // Build service info list including deleted service indicators
    List<AppointmentResponse.ServiceInfo> serviceInfos = new ArrayList<>();
    
    // Add resolved (active) services
    for (ServiceEntity service : resolvedServices) {
      serviceInfos.add(AppointmentResponse.ServiceInfo.builder()
          .id(service.getId())
          .name(service.getName())
          .deleted(false)
          .build());
    }
    
    // Check for deleted services: any service in the join table that wasn't resolved
    // Since services are EAGER-loaded, unresolvable ones just won't appear
    // We detect deleted services by comparing the raw set size
    // Note: With @SQLRestriction on ServiceEntity, deleted services are filtered automatically
    // We can detect them if the set has entries that fail to initialize
    // For simplicity, we rely on the resolved list — if a service was deleted, it won't appear
    
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
            : java.util.Collections.emptyList())
        .additionalReminderMinutes(appointment.getAdditionalReminderMinutes())
        .additionalReminderTemplate(appointment.getAdditionalReminderTemplate())
        .createdAt(appointment.getCreatedAt())
        .updatedAt(appointment.getUpdatedAt())
        .build();
  }

  private static final DateTimeFormatter DATE_TIME_FORMATTER = 
      DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");
  
  // Safe bounds for PostgreSQL timestamptz
  private static final OffsetDateTime MIN_DATE = OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime MAX_DATE = OffsetDateTime.of(3000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

  private String formatDateTime(OffsetDateTime dateTime) {
    if (dateTime == null) return "(not set)";
    return dateTime.atZoneSameInstant(TimeConfig.PH_ZONE).format(DATE_TIME_FORMATTER);
  }
}
