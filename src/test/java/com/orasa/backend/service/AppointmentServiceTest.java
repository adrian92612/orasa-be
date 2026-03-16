package com.orasa.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orasa.backend.common.AppointmentStatus;
import com.orasa.backend.common.AppointmentType;
import com.orasa.backend.common.UserRole;
import com.orasa.backend.domain.AppointmentEntity;
import com.orasa.backend.domain.BranchEntity;
import com.orasa.backend.domain.BusinessEntity;
import com.orasa.backend.domain.UserEntity;
import com.orasa.backend.domain.ServiceEntity;
import com.orasa.backend.dto.appointment.AppointmentResponse;
import com.orasa.backend.dto.appointment.CreateAppointmentRequest;
import com.orasa.backend.dto.appointment.UpdateAppointmentRequest;
import com.orasa.backend.dto.appointment.UpdateResult;
import com.orasa.backend.exception.ForbiddenException;
import com.orasa.backend.exception.InvalidAppointmentException;
import com.orasa.backend.repository.AppointmentRepository;
import com.orasa.backend.repository.BranchRepository;
import com.orasa.backend.repository.BusinessRepository;
import com.orasa.backend.repository.ServiceRepository;
import com.orasa.backend.repository.UserRepository;

import com.orasa.backend.service.sms.SmsService;
import com.orasa.backend.mapper.AppointmentMapper;
import com.orasa.backend.security.SecurityValidator;
import com.orasa.backend.service.helper.AppointmentChangeTracker;

@ExtendWith(MockitoExtension.class)
public class AppointmentServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ActivityLogService activityLogService;

    @Mock
    private SmsService smsService;

    @Mock
    private ServiceRepository serviceRepository;


    @Mock
    private Clock clock;

    @Mock
    private CacheService cacheService;

    private AppointmentMapper appointmentMapper;

    @Spy
    private SecurityValidator securityValidator = new SecurityValidator();

    @Spy
    private AppointmentChangeTracker changeTracker = new AppointmentChangeTracker();

    private AppointmentService appointmentService;

    @Captor
    private ArgumentCaptor<AppointmentEntity> appointmentCaptor;

    private UserEntity ownerUser;
    private UserEntity staffUser;
    private BusinessEntity business;
    private BranchEntity branch;
    private UUID ownerId;
    private UUID staffId;
    private UUID businessId;
    private UUID branchId;
    private OffsetDateTime now;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        staffId = UUID.randomUUID();
        businessId = UUID.randomUUID();
        branchId = UUID.randomUUID();

        now = OffsetDateTime.of(2026, 3, 2, 8, 0, 0, 0, ZoneOffset.UTC);

        business = BusinessEntity.builder()
                .name("Test Business")
                .build();
        business.setId(businessId);

        branch = BranchEntity.builder()
                .name("Test Branch")
                .business(business)
                .build();
        branch.setId(branchId);

        appointmentMapper = spy(new AppointmentMapper(serviceRepository));
        appointmentService = new AppointmentService(
                appointmentRepository,
                branchRepository,
                businessRepository,
                userRepository,
                activityLogService,
                smsService,
                serviceRepository,
                clock,
                cacheService,
                appointmentMapper,
                securityValidator,
                changeTracker
        );

        ownerUser = UserEntity.builder()
                .username("owneruser")
                .email("owner@test.com")
                .role(UserRole.OWNER)
                .business(business)
                .build();
        ownerUser.setId(ownerId);

        staffUser = UserEntity.builder()
                .username("staffuser")
                .role(UserRole.STAFF)
                .business(business)
                .branches(Set.of(branch))
                .build();
        staffUser.setId(staffId);
    }

    @Nested
    @DisplayName("Create Appointment")
    class CreateAppointmentTests {

        private CreateAppointmentRequest.CreateAppointmentRequestBuilder baseRequestBuilder() {
            return CreateAppointmentRequest.builder()
                    .businessId(businessId)
                    .branchId(branchId)
                    .customerName("John Doe")
                    .customerPhone("639123456789")
                    .isWalkin(false)
                    .startDateTime(now.plusDays(1));
        }

        @Test
        @DisplayName("Should successfully create a scheduled appointment with a service")
        void createScheduledAppointment_withService_success() {
            // Arrange
            ServiceEntity service = ServiceEntity.builder()
                    .name("Haircut")
                    .build();
            service.setId(UUID.randomUUID());

            CreateAppointmentRequest request = baseRequestBuilder()
                    .serviceIds(List.of(service.getId()))
                    .build();

            when(userRepository.findById(ownerId)).thenReturn(Optional.of(ownerUser));
            when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
            when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));
            when(serviceRepository.findAllById(List.of(service.getId()))).thenReturn(List.of(service));
            when(clock.instant()).thenReturn(now.toInstant());
            when(clock.getZone()).thenReturn(now.getOffset());

            AppointmentEntity savedAppointment = AppointmentEntity.builder()
                    .customerName(request.getCustomerName())
                    .startDateTime(request.getStartDateTime())

                    .status(AppointmentStatus.PENDING)
                    .type(AppointmentType.SCHEDULED)
                    .branch(branch)
                    .business(business)
                    .services(Set.of(service))
                    .build();
            savedAppointment.setId(UUID.randomUUID());

            when(appointmentRepository.save(any(AppointmentEntity.class))).thenReturn(savedAppointment);

            // Act
            AppointmentResponse response = appointmentService.createAppointment(ownerId, request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getCustomerName()).isEqualTo(request.getCustomerName());
            assertThat(response.getStatus()).isEqualTo(AppointmentStatus.PENDING);
            assertThat(response.getType()).isEqualTo(AppointmentType.SCHEDULED);

            verify(appointmentRepository).save(appointmentCaptor.capture());

            verify(activityLogService).logAppointmentCreated(eq(ownerUser), any(AppointmentEntity.class));
            verify(smsService).scheduleRemindersForAppointment(savedAppointment);
            verify(cacheService).evictAll(com.orasa.backend.common.CacheName.ANALYTICS, businessId);
        }

        @Test
        @DisplayName("Should successfully create a walkin appointment and not schedule reminders")
        void createWalkinAppointment_success() {
            // Arrange
            ServiceEntity service = ServiceEntity.builder()
                    .name("Walkin Service")
                    .build();
            service.setId(UUID.randomUUID());

            CreateAppointmentRequest request = baseRequestBuilder()
                    .isWalkin(true)
                    .startDateTime(now)
                    .serviceIds(List.of(service.getId()))
                    .build();

            when(userRepository.findById(staffId)).thenReturn(Optional.of(staffUser));
            when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
            when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));
            when(serviceRepository.findAllById(List.of(service.getId()))).thenReturn(List.of(service));

            AppointmentEntity savedAppointment = AppointmentEntity.builder()
                    .customerName(request.getCustomerName())
                    .startDateTime(request.getStartDateTime())

                    .status(AppointmentStatus.PENDING)
                    .type(AppointmentType.WALK_IN)
                    .branch(branch)
                    .business(business)
                    .services(Set.of(service))
                    .build();
            savedAppointment.setId(UUID.randomUUID());

            when(appointmentRepository.save(any(AppointmentEntity.class))).thenReturn(savedAppointment);

            // Act
            AppointmentResponse response = appointmentService.createAppointment(staffId, request);

            // Assert
            assertThat(response.getType()).isEqualTo(AppointmentType.WALK_IN);

            verify(activityLogService).logAppointmentCreated(eq(staffUser), any(AppointmentEntity.class));
            verify(smsService, never()).scheduleRemindersForAppointment(any());
        }

        @Test
        @DisplayName("Should throw InvalidAppointmentException if start time is in the past for scheduled appointment")
        void createScheduledAppointment_pastStartTime_throwsException() {
            // Arrange
            CreateAppointmentRequest request = baseRequestBuilder()
                    .startDateTime(now.minusHours(1)) // Past

                    .build();

            when(userRepository.findById(ownerId)).thenReturn(Optional.of(ownerUser));
            when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
            when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));
            when(clock.instant()).thenReturn(now.toInstant());
            when(clock.getZone()).thenReturn(now.getOffset());

            // Act & Assert
            assertThatThrownBy(() -> appointmentService.createAppointment(ownerId, request))
                    .isInstanceOf(InvalidAppointmentException.class)
                    .hasMessage("Appointment time must be in the future");
        }

        @Test
        @DisplayName("Should throw InvalidAppointmentException if no service is provided")
        void createAppointment_noService_throwsException() {
            // Arrange
            CreateAppointmentRequest request = baseRequestBuilder()
                    .serviceIds(null)
                    .build();

            when(userRepository.findById(ownerId)).thenReturn(Optional.of(ownerUser));
            when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
            when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));
            when(clock.instant()).thenReturn(now.toInstant());
            when(clock.getZone()).thenReturn(now.getOffset());

            // Act & Assert
            assertThatThrownBy(() -> appointmentService.createAppointment(ownerId, request))
                    .isInstanceOf(InvalidAppointmentException.class)
                    .hasMessage("At least one service is required");
        }

        @Test
        @DisplayName("Should throw ForbiddenException if user tries to create appointment in unassigned branch")
        void createAppointment_unassignedBranch_throwsForbidden() {
            // Arrange
            CreateAppointmentRequest request = baseRequestBuilder().build();

            // Re-create staff with different branch
            BranchEntity otherBranch = BranchEntity.builder().name("Other").business(business).build();
            otherBranch.setId(UUID.randomUUID());

            staffUser.setBranches(Set.of(otherBranch));

            when(userRepository.findById(staffId)).thenReturn(Optional.of(staffUser));
            when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
            when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));

            // Act & Assert
            assertThatThrownBy(() -> appointmentService.createAppointment(staffId, request))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("You are not assigned to this branch");
        }

        @Test
        @DisplayName("Should throw InvalidAppointmentException if branch doesn't belong to business")
        void createAppointment_branchNotBelongToBusiness_throwsException() {
            // Arrange
            CreateAppointmentRequest request = baseRequestBuilder().build();

            BusinessEntity otherBusiness = BusinessEntity.builder().name("Other").build();
            otherBusiness.setId(UUID.randomUUID());

            BranchEntity detachedBranch = BranchEntity.builder().business(otherBusiness).build();
            detachedBranch.setId(branchId);

            when(userRepository.findById(ownerId)).thenReturn(Optional.of(ownerUser));
            when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
            when(branchRepository.findById(branchId)).thenReturn(Optional.of(detachedBranch));

            // Act & Assert
            assertThatThrownBy(() -> appointmentService.createAppointment(ownerId, request))
                    .isInstanceOf(InvalidAppointmentException.class)
                    .hasMessage("Branch does not belong to the specified business");
        }
    }

    @Nested
    @DisplayName("Delete Appointment")
    class DeleteAppointmentTests {

        @Test
        @DisplayName("Should successfully soft delete appointment if user is owner of business")
        void deleteAppointment_owner_success() {
            // Arrange
            AppointmentEntity appointment = AppointmentEntity.builder()
                    .business(business)
                    .branch(branch)
                    .build();
            appointment.setId(UUID.randomUUID());

            when(userRepository.findById(ownerId)).thenReturn(Optional.of(ownerUser));
            when(appointmentRepository.findById(appointment.getId())).thenReturn(Optional.of(appointment));

            // Act
            appointmentService.deleteAppointment(ownerId, appointment.getId());

            // Assert
            verify(activityLogService).logAppointmentDeleted(ownerUser, appointment);
            verify(smsService).cancelRemindersForAppointment(appointment.getId());
            verify(appointmentRepository).delete(appointment);
            verify(cacheService).evictAll(com.orasa.backend.common.CacheName.ANALYTICS, business.getId());
        }

        @Test
        @DisplayName("Should throw ForbiddenException if user is staff")
        void deleteAppointment_staff_throwsForbidden() {
            // Arrange
            AppointmentEntity appointment = AppointmentEntity.builder()
                    .business(business)
                    .branch(branch)
                    .build();
            appointment.setId(UUID.randomUUID());

            when(userRepository.findById(staffId)).thenReturn(Optional.of(staffUser));
            when(appointmentRepository.findById(appointment.getId())).thenReturn(Optional.of(appointment));

            // Act & Assert
            assertThatThrownBy(() -> appointmentService.deleteAppointment(staffId, appointment.getId()))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Only business owners can delete appointments");

            verify(appointmentRepository, never()).delete(any(AppointmentEntity.class));
        }

        @Test
        @DisplayName("Should throw ForbiddenException if owner is from different business")
        void deleteAppointment_differentBusiness_throwsForbidden() {
            // Arrange
            BusinessEntity otherBusiness = BusinessEntity.builder().name("Other").build();
            otherBusiness.setId(UUID.randomUUID());

            AppointmentEntity appointment = AppointmentEntity.builder()
                    .business(otherBusiness)
                    .branch(branch)
                    .build();
            appointment.setId(UUID.randomUUID());

            when(userRepository.findById(ownerId)).thenReturn(Optional.of(ownerUser));
            when(appointmentRepository.findById(appointment.getId())).thenReturn(Optional.of(appointment));

            // Act & Assert
            assertThatThrownBy(() -> appointmentService.deleteAppointment(ownerId, appointment.getId()))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("You can only delete appointments for your own business");

            verify(appointmentRepository, never()).delete(any(AppointmentEntity.class));
        }
    }

    @Nested
    @DisplayName("Update Appointment")
    class UpdateAppointmentTests {

        private AppointmentEntity existingAppointment;
        private UUID appointmentId;

        @BeforeEach
        void setUpAppointment() {
            appointmentId = UUID.randomUUID();
            existingAppointment = AppointmentEntity.builder()
                    .business(business)
                    .branch(branch)
                    .customerName("Old Name")
                    .customerPhone("639000000000")
                    .status(AppointmentStatus.PENDING)
                    .type(AppointmentType.SCHEDULED)
                    .startDateTime(now.plusDays(1))

                    .build();
            existingAppointment.setId(appointmentId);
        }

        @Test
        @DisplayName("Should update customer name and phone and log changes")
        void updateAppointment_customerNameAndPhone_updatesAndLogs() {
            // Arrange
            UpdateAppointmentRequest request = UpdateAppointmentRequest.builder()
                    .customerName("New Name")
                    .customerPhone("639999999999")
                    .startDateTime(existingAppointment.getStartDateTime())

                    .status(AppointmentStatus.PENDING)
                    .build();

            when(userRepository.findById(ownerId)).thenReturn(Optional.of(ownerUser));
            when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(existingAppointment));
            when(appointmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            // Act
            UpdateResult result = appointmentService.updateAppointment(ownerId,
                    appointmentId, request);

            // Assert
            assertThat(result.isModified()).isTrue();
            assertThat(existingAppointment.getCustomerName()).isEqualTo("New Name");
            assertThat(existingAppointment.getCustomerPhone()).isEqualTo("639999999999");
            verify(appointmentRepository).save(existingAppointment);
            verify(activityLogService).logAppointmentUpdated(eq(ownerUser), any(), any());
            // No status change, no SMS cancel
            verify(smsService, never()).cancelRemindersForAppointment(any());
        }

        @Test
        @DisplayName("Should return wasUpdated=false when no fields changed")
        void updateAppointment_noChanges_returnsNotUpdated() {
            // Arrange — identical values
            UpdateAppointmentRequest request = UpdateAppointmentRequest.builder()
                    .customerName(existingAppointment.getCustomerName())
                    .customerPhone(existingAppointment.getCustomerPhone())
                    .startDateTime(existingAppointment.getStartDateTime())

                    .status(existingAppointment.getStatus())
                    .build();

            when(userRepository.findById(ownerId)).thenReturn(Optional.of(ownerUser));
            when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(existingAppointment));

            // Act
            UpdateResult result = appointmentService.updateAppointment(ownerId,
                    appointmentId, request);

            // Assert
            assertThat(result.isModified()).isFalse();
            verify(appointmentRepository, never()).save(any());
            verify(activityLogService, never()).logAppointmentUpdated(any(), any(), any());
        }

        @Test
        @DisplayName("Should throw InvalidAppointmentException when trying to change appointment type")
        void updateAppointment_changeType_throwsException() {
            // Arrange — existing is SCHEDULED, request claims WALK_IN
            UpdateAppointmentRequest request = UpdateAppointmentRequest.builder()
                    .customerName(existingAppointment.getCustomerName())
                    .customerPhone(existingAppointment.getCustomerPhone())
                    .startDateTime(existingAppointment.getStartDateTime())

                    .status(AppointmentStatus.PENDING)
                    .isWalkin(true) // SCHEDULED -> WALK_IN: not allowed
                    .build();

            when(userRepository.findById(ownerId)).thenReturn(Optional.of(ownerUser));
            when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(existingAppointment));

            // Act & Assert
            assertThatThrownBy(() -> appointmentService.updateAppointment(ownerId, appointmentId, request))
                    .isInstanceOf(InvalidAppointmentException.class)
                    .hasMessage("Cannot change appointment type after creation");
        }

        @Test
        @DisplayName("Should throw InvalidAppointmentException when updating start time to the past")
        void updateAppointment_pastStartTime_throwsException() {
            // Arrange
            UpdateAppointmentRequest request = UpdateAppointmentRequest.builder()
                    .customerName(existingAppointment.getCustomerName())
                    .customerPhone(existingAppointment.getCustomerPhone())
                    .startDateTime(now.minusHours(1)) // past

                    .status(AppointmentStatus.PENDING)
                    .build();

            when(userRepository.findById(ownerId)).thenReturn(Optional.of(ownerUser));
            when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(existingAppointment));
            when(clock.instant()).thenReturn(now.toInstant());
            when(clock.getZone()).thenReturn(now.getOffset());

            // Act & Assert
            assertThatThrownBy(() -> appointmentService.updateAppointment(ownerId, appointmentId, request))
                    .isInstanceOf(InvalidAppointmentException.class)
                    .hasMessage("Start time must be in the future");
        }

        @Test
        @DisplayName("Should cancel SMS reminders when status changes to CANCELLED")
        void updateAppointment_statusChangeToCancelled_cancelsReminders() {
            // Arrange
            UpdateAppointmentRequest request = UpdateAppointmentRequest.builder()
                    .customerName(existingAppointment.getCustomerName())
                    .customerPhone(existingAppointment.getCustomerPhone())
                    .startDateTime(existingAppointment.getStartDateTime())

                    .status(AppointmentStatus.CANCELLED) // <- status change
                    .build();

            when(userRepository.findById(ownerId)).thenReturn(Optional.of(ownerUser));
            when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(existingAppointment));
            when(appointmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            // Act
            appointmentService.updateAppointment(ownerId, appointmentId, request);

            // Assert — status change logs via logAppointmentStatusChanged
            verify(activityLogService).logAppointmentStatusChanged(eq(ownerUser), any(), eq("PENDING"),
                    eq("CANCELLED"));
            verify(smsService).cancelRemindersForAppointment(appointmentId);
            verify(smsService, never()).scheduleRemindersForAppointment(any());
        }

        @Test
        @DisplayName("Should reschedule SMS when start time changes for PENDING appointment")
        void updateAppointment_startTimeChanged_reschedulesReminders() {
            // Arrange
            OffsetDateTime newStart = now.plusDays(3);
            UpdateAppointmentRequest request = UpdateAppointmentRequest.builder()
                    .customerName(existingAppointment.getCustomerName())
                    .customerPhone(existingAppointment.getCustomerPhone())
                    .startDateTime(newStart)

                    .status(AppointmentStatus.PENDING)
                    .build();

            when(userRepository.findById(ownerId)).thenReturn(Optional.of(ownerUser));
            when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(existingAppointment));
            when(appointmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(clock.instant()).thenReturn(now.toInstant());
            when(clock.getZone()).thenReturn(now.getOffset());

            // Act
            appointmentService.updateAppointment(ownerId, appointmentId, request);

            // Assert — cancel old + schedule new
            verify(smsService).cancelRemindersForAppointment(appointmentId);
            verify(smsService).scheduleRemindersForAppointment(existingAppointment);
        }

        @Test
        @DisplayName("Should throw ForbiddenException if staff tries to update appointment in unassigned branch")
        void updateAppointment_staffUnassignedBranch_throwsForbidden() {
            // Arrange
            BranchEntity otherBranch = BranchEntity.builder().name("Other").business(business).build();
            otherBranch.setId(UUID.randomUUID());
            staffUser.setBranches(Set.of(otherBranch)); // staff not assigned to 'branch'

            UpdateAppointmentRequest request = UpdateAppointmentRequest.builder()
                    .customerName("Name")
                    .customerPhone("639000000000")
                    .startDateTime(now.plusDays(1))

                    .status(AppointmentStatus.PENDING)
                    .build();

            when(userRepository.findById(staffId)).thenReturn(Optional.of(staffUser));
            when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(existingAppointment));

            // Act & Assert
            assertThatThrownBy(() -> appointmentService.updateAppointment(staffId, appointmentId, request))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("You are not assigned to this branch");
        }
    }

    @Nested
    @DisplayName("Update Appointment Status")
    class UpdateAppointmentStatusTests {

        private AppointmentEntity existingAppointment;
        private UUID appointmentId;

        @BeforeEach
        void setUpAppointment() {
            appointmentId = UUID.randomUUID();
            existingAppointment = AppointmentEntity.builder()
                    .business(business)
                    .branch(branch)
                    .customerName("John Doe")
                    .customerPhone("639123456789")
                    .status(AppointmentStatus.PENDING)
                    .type(AppointmentType.SCHEDULED)
                    .startDateTime(now.plusDays(1))

                    .build();
            existingAppointment.setId(appointmentId);
        }

        @Test
        @DisplayName("Should update status and cancel reminders when status changes to COMPLETED")
        void updateAppointmentStatus_toCompleted_cancelsReminders() {
            when(userRepository.findById(ownerId)).thenReturn(Optional.of(ownerUser));
            when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(existingAppointment));
            when(appointmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            appointmentService.updateAppointmentStatus(ownerId, appointmentId, AppointmentStatus.COMPLETED);

            assertThat(existingAppointment.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
            verify(smsService).cancelRemindersForAppointment(appointmentId);
            verify(smsService, never()).scheduleRemindersForAppointment(any());
            verify(activityLogService).logAppointmentStatusChanged(eq(ownerUser), any(), eq("PENDING"),
                    eq("COMPLETED"));
        }

        @Test
        @DisplayName("Should reschedule reminders when reactivating from CANCELLED to CONFIRMED")
        void updateAppointmentStatus_reactivateFromCancelled_reschedulesReminders() {
            existingAppointment.setStatus(AppointmentStatus.CANCELLED);

            when(userRepository.findById(ownerId)).thenReturn(Optional.of(ownerUser));
            when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(existingAppointment));
            when(appointmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            appointmentService.updateAppointmentStatus(ownerId, appointmentId, AppointmentStatus.CONFIRMED);

            assertThat(existingAppointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
            verify(smsService, never()).cancelRemindersForAppointment(any());
            verify(smsService).scheduleRemindersForAppointment(existingAppointment);
        }

        @Test
        @DisplayName("Should return current state without saving when status is unchanged")
        void updateAppointmentStatus_sameStatus_noSave() {
            // PENDING -> PENDING
            when(userRepository.findById(ownerId)).thenReturn(Optional.of(ownerUser));
            when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(existingAppointment));

            AppointmentResponse response = appointmentService.updateAppointmentStatus(ownerId, appointmentId,
                    AppointmentStatus.PENDING);

            assertThat(response).isNotNull();
            verify(appointmentRepository, never()).save(any());
            verify(smsService, never()).cancelRemindersForAppointment(any());
        }

        @Test
        @DisplayName("Should NOT reschedule reminders when reactivating a walk-in appointment")
        void updateAppointmentStatus_reactivateWalkIn_doesNotReschedule() {
            existingAppointment.setType(AppointmentType.WALK_IN);
            existingAppointment.setStatus(AppointmentStatus.CANCELLED);

            when(userRepository.findById(ownerId)).thenReturn(Optional.of(ownerUser));
            when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(existingAppointment));
            when(appointmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            appointmentService.updateAppointmentStatus(ownerId, appointmentId, AppointmentStatus.CONFIRMED);

            // Walk-ins don't get reminders
            verify(smsService, never()).scheduleRemindersForAppointment(any());
            verify(smsService, never()).cancelRemindersForAppointment(any());
        }
    }
}
