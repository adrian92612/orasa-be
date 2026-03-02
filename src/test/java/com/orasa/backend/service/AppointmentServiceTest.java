package com.orasa.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import com.orasa.backend.exception.ForbiddenException;
import com.orasa.backend.exception.InvalidAppointmentException;
import com.orasa.backend.repository.AppointmentRepository;
import com.orasa.backend.repository.BranchRepository;
import com.orasa.backend.repository.BusinessRepository;
import com.orasa.backend.repository.ServiceRepository;
import com.orasa.backend.repository.UserRepository;
import com.orasa.backend.repository.BusinessReminderConfigRepository;
import com.orasa.backend.service.sms.SmsService;

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
    private BusinessReminderConfigRepository reminderConfigRepository;

    @Mock
    private Clock clock;

    @Mock
    private CacheService cacheService;

    @InjectMocks
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
                .durationMinutes(30)
                .build();
            service.setId(UUID.randomUUID());

            CreateAppointmentRequest request = baseRequestBuilder()
                .serviceId(service.getId())
                .build();

            when(userRepository.findById(ownerId)).thenReturn(Optional.of(ownerUser));
            when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
            when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));
            when(serviceRepository.findById(service.getId())).thenReturn(Optional.of(service));
            when(clock.instant()).thenReturn(now.toInstant());
            when(clock.getZone()).thenReturn(now.getOffset());

            AppointmentEntity savedAppointment = AppointmentEntity.builder()
                .customerName(request.getCustomerName())
                .startDateTime(request.getStartDateTime())
                .endDateTime(request.getStartDateTime().plusMinutes(service.getDurationMinutes()))
                .status(AppointmentStatus.PENDING)
                .type(AppointmentType.SCHEDULED)
                .branch(branch)
                .business(business)
                .service(service)
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
            AppointmentEntity captured = appointmentCaptor.getValue();
            assertThat(captured.getEndDateTime()).isEqualTo(request.getStartDateTime().plusMinutes(30));
            
            verify(activityLogService).logAppointmentCreated(eq(ownerUser), any(AppointmentEntity.class));
            verify(smsService).scheduleRemindersForAppointment(savedAppointment);
            verify(cacheService).evictAll(com.orasa.backend.common.CacheName.ANALYTICS);
        }
        
        @Test
        @DisplayName("Should successfully create a walkin appointment and not schedule reminders")
        void createWalkinAppointment_success() {
            // Arrange
            CreateAppointmentRequest request = baseRequestBuilder()
                .isWalkin(true)
                .startDateTime(now)
                .endDateTime(now.plusMinutes(60))
                .build();

            when(userRepository.findById(staffId)).thenReturn(Optional.of(staffUser));
            when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
            when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));
            
            AppointmentEntity savedAppointment = AppointmentEntity.builder()
                .customerName(request.getCustomerName())
                .startDateTime(request.getStartDateTime())
                .endDateTime(request.getEndDateTime())
                .status(AppointmentStatus.PENDING)
                .type(AppointmentType.WALK_IN)
                .branch(branch)
                .business(business)
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
                .endDateTime(now.plusHours(1))
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
        @DisplayName("Should throw InvalidAppointmentException if no service and no end time is provided")
        void createAppointment_noServiceNoEndTime_throwsException() {
            // Arrange
            CreateAppointmentRequest request = baseRequestBuilder()
                .serviceId(null)
                .endDateTime(null)
                .build();

            when(userRepository.findById(ownerId)).thenReturn(Optional.of(ownerUser));
            when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
            when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));
            when(clock.instant()).thenReturn(now.toInstant());
            when(clock.getZone()).thenReturn(now.getOffset());

            // Act & Assert
            assertThatThrownBy(() -> appointmentService.createAppointment(ownerId, request))
                .isInstanceOf(InvalidAppointmentException.class)
                .hasMessage("End time or Service is required");
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
            verify(cacheService).evictAll(com.orasa.backend.common.CacheName.ANALYTICS);
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
}
