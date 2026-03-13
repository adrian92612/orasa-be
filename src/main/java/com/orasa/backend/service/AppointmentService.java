package com.orasa.backend.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orasa.backend.common.AppointmentStatus;
import com.orasa.backend.common.AppointmentType;
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
import com.orasa.backend.exception.InvalidAppointmentException;
import com.orasa.backend.exception.ResourceNotFoundException;
import com.orasa.backend.repository.AppointmentRepository;
import com.orasa.backend.repository.AppointmentSpecification;
import com.orasa.backend.repository.BranchRepository;
import com.orasa.backend.repository.BusinessRepository;
import com.orasa.backend.repository.UserRepository;
import com.orasa.backend.repository.ServiceRepository;
import com.orasa.backend.domain.ServiceEntity;
import com.orasa.backend.service.sms.SmsService;
import com.orasa.backend.config.TimeConfig;
import com.orasa.backend.common.CacheName;
import com.orasa.backend.mapper.AppointmentMapper;
import com.orasa.backend.security.SecurityValidator;
import com.orasa.backend.service.helper.AppointmentChangeTracker;


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

  private final Clock clock;
  private final CacheService cacheService;

  // New dependencies
  private final AppointmentMapper appointmentMapper;
  private final SecurityValidator securityValidator;
  private final AppointmentChangeTracker changeTracker;

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

    securityValidator.validateBranchAccess(user, branch);

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
        .remindersEnabled(request.getRemindersEnabled() != null ? request.getRemindersEnabled() : true)
        .status(AppointmentStatus.PENDING)
        .type(request.getIsWalkin() ? AppointmentType.WALK_IN : AppointmentType.SCHEDULED)
        .additionalReminderMinutes(reminderMinutes)
        .additionalReminderTemplate(request.getAdditionalReminderTemplate());

    // Resolve services
    List<UUID> serviceIds = request.getServiceIds();
    if (serviceIds == null || serviceIds.isEmpty()) {
      throw new InvalidAppointmentException("At least one service is required");
    }
    List<ServiceEntity> serviceEntities = serviceRepository.findAllById(serviceIds);
    if (serviceEntities.size() != serviceIds.size()) {
      throw new ResourceNotFoundException("One or more services not found");
    }
    builder.services(new HashSet<>(serviceEntities));

    AppointmentEntity saved = appointmentRepository.save(builder.build());
    activityLogService.logAppointmentCreated(user, saved);

    if (!request.getIsWalkin()) {
        try {
            smsService.scheduleRemindersForAppointment(saved);
        } catch (Exception e) {
            log.error("Failed to schedule reminders for appointment {}: {}", saved.getId(), e.getMessage(), e);
        }
    }

    cacheService.evictAll(CacheName.ANALYTICS, saved.getBusiness().getId());
    return appointmentMapper.mapToResponse(saved);
  }

  @Transactional
  public UpdateResult updateAppointment(UUID userId, UUID id, UpdateAppointmentRequest request) {
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    AppointmentEntity appointment = appointmentRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

    securityValidator.validateBranchAccess(user, appointment.getBranch());

    // Capture state for change tracking
    List<ServiceEntity> beforeServices = appointmentMapper.resolveServices(appointment);
    AppointmentStatus beforeStatus = appointment.getStatus();
    
    // Validate type change
    boolean isOriginallyWalkin = appointment.getType() == AppointmentType.WALK_IN;
    if (request.getIsWalkin() != null && request.getIsWalkin() != isOriginallyWalkin) {
        throw new InvalidAppointmentException("Cannot change appointment type after creation");
    }

    // Track field changes and update entity
    AppointmentChangeTracker.ChangeResult result = changeTracker.trackChanges(appointment, request, beforeServices);

    if (result.isStartTimeChanged() && appointment.getStartDateTime().isBefore(OffsetDateTime.now(clock))) {
        throw new InvalidAppointmentException("Start time must be in the future");
    }

    if (!result.hasChanges()) {
      return new UpdateResult(appointmentMapper.mapToResponse(appointment), false);
    }

    // Handle complex updates (services and reminders) which need repository access
    if (result.isServicesChanged()) {
      List<ServiceEntity> newServices = serviceRepository.findAllById(request.getServiceIds());
      if (newServices.size() != request.getServiceIds().size()) {
        throw new ResourceNotFoundException("One or more services not found");
      }
      
      String beforeServiceNames = beforeServices.isEmpty() ? "(none)" : 
          beforeServices.stream().map(ServiceEntity::getName).collect(Collectors.joining(", "));
      String afterServiceNames = newServices.stream().map(ServiceEntity::getName).collect(Collectors.joining(", "));
      
      result.getChanges().add(FieldChange.builder()
          .field("Services")
          .before(beforeServiceNames)
          .after(afterServiceNames)
          .build());
      appointment.setServices(new HashSet<>(newServices));
    }

    if (result.isRemindersEnabledChanged()) {
      appointment.setRemindersEnabled(request.getRemindersEnabled());
      result.getChanges().add(FieldChange.builder()
          .field("Reminders Enabled")
          .before(String.valueOf(!request.getRemindersEnabled()))
          .after(String.valueOf(request.getRemindersEnabled()))
          .build());
    }

    AppointmentEntity saved = appointmentRepository.save(appointment);
    
    // Log changes
    if (request.getStatus() != null && !request.getStatus().equals(beforeStatus)) {
      activityLogService.logAppointmentStatusChanged(user, saved, beforeStatus.name(), request.getStatus().name());
    } else {
      activityLogService.logAppointmentUpdated(user, saved, FieldChange.toJson(result.getChanges()));
    }
    
    // Handle reminder scheduling
    handleReminderUpdates(saved, result);

    cacheService.evictAll(CacheName.ANALYTICS, saved.getBusiness().getId());
    return new UpdateResult(appointmentMapper.mapToResponse(saved), true);
  }

  private void handleReminderUpdates(AppointmentEntity saved, AppointmentChangeTracker.ChangeResult result) {
    boolean statusCancelledOrCompleted = saved.getStatus() == AppointmentStatus.CANCELLED || 
                                         saved.getStatus() == AppointmentStatus.COMPLETED;
    boolean reminderSettingsChanged = result.isStartTimeChanged() || 
                                     result.isRemindersEnabledChanged() || 
                                     result.isAdditionalReminderChanged();
    
    if (statusCancelledOrCompleted) {
        smsService.cancelRemindersForAppointment(saved.getId());
    } else if (reminderSettingsChanged && 
             (saved.getStatus() == AppointmentStatus.PENDING || saved.getStatus() == AppointmentStatus.CONFIRMED)) {
        smsService.cancelRemindersForAppointment(saved.getId());
        smsService.scheduleRemindersForAppointment(saved);
    }
  }

  @Transactional
  public AppointmentResponse updateAppointmentStatus(UUID userId, UUID id, AppointmentStatus newStatus) {
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    AppointmentEntity appointment = appointmentRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

    securityValidator.validateBranchAccess(user, appointment.getBranch());

    AppointmentStatus beforeStatus = appointment.getStatus();
    if (beforeStatus == newStatus) {
        return appointmentMapper.mapToResponse(appointment);
    }

    appointment.setStatus(newStatus);
    AppointmentEntity saved = appointmentRepository.save(appointment);
    activityLogService.logAppointmentStatusChanged(user, saved, beforeStatus.name(), newStatus.name());

    handleStatusChangeReminders(saved, beforeStatus, newStatus);

    cacheService.evictAll(CacheName.ANALYTICS, saved.getBusiness().getId());
    return appointmentMapper.mapToResponse(saved);
  }

  private void handleStatusChangeReminders(AppointmentEntity saved, AppointmentStatus beforeStatus, AppointmentStatus newStatus) {
    boolean isNewStatusTerminal = newStatus == AppointmentStatus.CANCELLED || newStatus == AppointmentStatus.COMPLETED;
    boolean wasTerminal = beforeStatus == AppointmentStatus.CANCELLED || beforeStatus == AppointmentStatus.COMPLETED;
    
    if (isNewStatusTerminal) {
        smsService.cancelRemindersForAppointment(saved.getId());
    } else if (wasTerminal && saved.getType() != AppointmentType.WALK_IN) {
        try {
            smsService.scheduleRemindersForAppointment(saved);
        } catch (Exception e) {
            log.error("Failed to reschedule reminders on status update for appointment {}: {}", saved.getId(), e.getMessage(), e);
        }
    }
  }

  public AppointmentResponse getAppointmentById(UUID userId, UUID id) {
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    AppointmentEntity appointment = appointmentRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
    
    securityValidator.validateBranchAccess(user, appointment.getBranch());
    return appointmentMapper.mapToResponse(appointment);
  }

  public Page<AppointmentResponse> getAppointmentsByBranch(UUID userId, UUID branchId, Pageable pageable) {
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    BranchEntity branch = branchRepository.findById(branchId)
        .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));

    securityValidator.validateBranchAccess(user, branch);
    return appointmentRepository.findByBranchId(branchId, pageable).map(appointmentMapper::mapToResponse);
  }

  public Page<AppointmentResponse> getAppointmentsByBusiness(UUID userId, UUID businessId, Pageable pageable) {
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    
    securityValidator.validateBusinessAccess(user, businessId);

    if (user.getRole() == UserRole.STAFF) {
        List<UUID> branchIds = user.getBranches().stream().map(BranchEntity::getId).toList();
        return appointmentRepository.findByBusinessIdAndBranchIdIn(businessId, branchIds, pageable).map(appointmentMapper::mapToResponse);
    }

    return appointmentRepository.findByBusinessId(businessId, pageable).map(appointmentMapper::mapToResponse);
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

    securityValidator.validateBranchAccess(user, branch);

    OffsetDateTime start = startDate != null ? startDate.atStartOfDay(TimeConfig.PH_ZONE).toOffsetDateTime() : MIN_DATE;
    OffsetDateTime end = endDate != null ? endDate.plusDays(1).atStartOfDay(TimeConfig.PH_ZONE).toOffsetDateTime() : MAX_DATE;

    Page<AppointmentEntity> page = appointmentRepository.findAll(
        AppointmentSpecification.buildSearchSpec(branchId, null, null, search, status, type, start, end),
        pageable
    );
    
    if (!page.getContent().isEmpty()) {
        List<UUID> ids = page.getContent().stream().map(AppointmentEntity::getId).toList();
        appointmentRepository.findAllByIdWithAssociations(ids);
    }
    
    return page.map(appointmentMapper::mapToResponse);
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
    securityValidator.validateBusinessAccess(user, businessId);

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
        List<UUID> ids = page.getContent().stream().map(AppointmentEntity::getId).toList();
        appointmentRepository.findAllByIdWithAssociations(ids);
    }
    
    return page.map(appointmentMapper::mapToResponse);
  }

  @Transactional
  public void deleteAppointment(UUID userId, UUID id) {
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    AppointmentEntity appointment = appointmentRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

    securityValidator.validateOwnerOnly(user, "delete appointments");
    securityValidator.validateBusinessAccess(user, appointment.getBusiness().getId(), "You can only delete appointments for your own business");

    activityLogService.logAppointmentDeleted(user, appointment);
    smsService.cancelRemindersForAppointment(id);

    appointmentRepository.delete(appointment);
    cacheService.evictAll(CacheName.ANALYTICS, appointment.getBusiness().getId());
  }

  // Safe bounds for PostgreSQL timestamptz
  private static final OffsetDateTime MIN_DATE = OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime MAX_DATE = OffsetDateTime.of(3000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
}
