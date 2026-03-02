package com.orasa.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.orasa.backend.domain.BranchEntity;
import com.orasa.backend.domain.BusinessEntity;
import com.orasa.backend.domain.UserEntity;
import com.orasa.backend.dto.business.BusinessResponse;
import com.orasa.backend.dto.business.CreateBusinessRequest.BranchData;
import com.orasa.backend.dto.business.CreateBusinessRequest;
import com.orasa.backend.exception.BusinessException;
import com.orasa.backend.exception.ResourceNotFoundException;
import com.orasa.backend.repository.BranchRepository;
import com.orasa.backend.repository.BusinessRepository;
import com.orasa.backend.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
public class BusinessServiceTest {

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private ActivityLogService activityLogService;

    @Mock
    private CacheService cacheService;

    @Mock
    private UserService userService;

    @InjectMocks
    private BusinessService businessService;

    private UserEntity owner;
    private UUID ownerId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        owner = UserEntity.builder()
            .username("owner")
            .branches(new HashSet<>())
            .build();
        owner.setId(ownerId);
    }

    @Nested
    @DisplayName("Create Business with Branch")
    class CreateBusinessWithBranchTests {

        @Test
        @DisplayName("Should successfully create business and branch atomically")
        void createBusinessWithBranch_success() {
            // Arrange
            BranchData branchInfo = BranchData.builder()
                .name("Main Branch")
                .address("123 Test St")
                .phoneNumber("639123456789")
                .build();

            CreateBusinessRequest request = CreateBusinessRequest.builder()
                .name("Test Business")
                .branch(branchInfo)
                .build();

            when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));

            BusinessEntity savedBusiness = BusinessEntity.builder()
                .name("Test Business")
                .build();
            savedBusiness.setId(UUID.randomUUID());

            BranchEntity savedBranch = BranchEntity.builder()
                .name("Main Branch")
                .business(savedBusiness)
                .build();
            savedBranch.setId(UUID.randomUUID());

            when(businessRepository.save(any(BusinessEntity.class))).thenReturn(savedBusiness);
            when(branchRepository.save(any(BranchEntity.class))).thenReturn(savedBranch);

            // Act
            // Need to mock TransactionSynchronizationManager if it's used directly
            try {
                TransactionSynchronizationManager.initSynchronization();
                BusinessResponse response = businessService.createBusinessWithBranch(ownerId, request);
                
                // Assert
                assertThat(response).isNotNull();
                assertThat(response.getId()).isEqualTo(savedBusiness.getId());
                assertThat(response.getFirstBranchId()).isEqualTo(savedBranch.getId());
                assertThat(response.getName()).isEqualTo("Test Business");

                verify(activityLogService).logBusinessCreated(owner, savedBusiness);
                verify(activityLogService).logBranchCreated(owner, savedBusiness, savedBranch);
                verify(userRepository).save(owner);
                assertThat(owner.getBusiness()).isEqualTo(savedBusiness);
                assertThat(owner.getBranches()).contains(savedBranch);
            } finally {
                TransactionSynchronizationManager.clear();
            }
        }

        @Test
        @DisplayName("Should throw BusinessException if user already has a business")
        void createBusinessWithBranch_alreadyHasBusiness_throwsException() {
            // Arrange
            owner.setBusiness(BusinessEntity.builder().build());
            when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
            
            CreateBusinessRequest request = CreateBusinessRequest.builder().build();

            // Act & Assert
            assertThatThrownBy(() -> businessService.createBusinessWithBranch(ownerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("User already has a business");
                
            verify(businessRepository, never()).save(any());
        }
        
        @Test
        @DisplayName("Should throw ResourceNotFoundException if user doesn't exist")
        void createBusinessWithBranch_userNotFound_throwsException() {
            // Arrange
            when(userRepository.findById(ownerId)).thenReturn(Optional.empty());
            CreateBusinessRequest request = CreateBusinessRequest.builder().build();

            // Act & Assert
            assertThatThrownBy(() -> businessService.createBusinessWithBranch(ownerId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
        }
    }
}
