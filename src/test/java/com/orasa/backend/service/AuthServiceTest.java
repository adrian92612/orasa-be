package com.orasa.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.orasa.backend.common.UserRole;
import com.orasa.backend.domain.BusinessEntity;
import com.orasa.backend.domain.UserEntity;
import com.orasa.backend.dto.auth.AuthResponse;
import com.orasa.backend.dto.auth.StaffLoginRequest;
import com.orasa.backend.repository.UserRepository;
import com.orasa.backend.security.JwtService;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private GoogleOAuthService googleOAuthService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ActivityLogService activityLogService;

    @InjectMocks
    private AuthService authService;

    private UserEntity owner;
    private UserEntity staff;
    private BusinessEntity business;
    private UUID ownerId;
    private UUID staffId;
    private UUID businessId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        staffId = UUID.randomUUID();
        businessId = UUID.randomUUID();

        business = BusinessEntity.builder()
            .name("Test Business")
            .build();
        business.setId(businessId);

        owner = UserEntity.builder()
            .username("owner@test.com")
            .email("owner@test.com")
            .role(UserRole.OWNER)
            .business(business)
            .build();
        owner.setId(ownerId);

        staff = UserEntity.builder()
            .username("staffuser")
            .role(UserRole.STAFF)
            .business(business)
            .build();
        staff.setId(staffId);
    }

    @Test
    @DisplayName("Should successfully login staff and generate JWT")
    void loginStaff_success() {
        // Arrange
        StaffLoginRequest request = new StaffLoginRequest();
        request.setUsername("staffuser");
        request.setPassword("password");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
            .thenReturn(new UsernamePasswordAuthenticationToken("staffuser", "password"));
        
        when(userRepository.findByUsernameWithRelations("staffuser")).thenReturn(Optional.of(staff));
        when(jwtService.generateToken(staff.getId(), staff.getUsername(), staff.getRole().name(), staff.getBusiness().getId(), staff.getBusiness().getName()))
            .thenReturn("mock-jwt-token");

        // Act
        AuthService.LoginResult result = authService.loginStaff(request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.token()).isEqualTo("mock-jwt-token");
        
        AuthResponse response = result.response();
        assertThat(response.getUserId()).isEqualTo(staffId);
        assertThat(response.getUsername()).isEqualTo("staffuser");
        assertThat(response.getRole()).isEqualTo(UserRole.STAFF);
        assertThat(response.getBusinessId()).isEqualTo(businessId);

        verify(activityLogService).logUserLogin(staff, business);
    }

    @Test
    @DisplayName("Should login via Google and generate JWT for existing owner")
    void loginWithGoogle_existingOwner_success() {
        // Arrange
        String code = "google-auth-code";
        String email = "owner@test.com";

        GoogleIdToken.Payload mockPayload = new GoogleIdToken.Payload();
        mockPayload.setEmail(email);

        when(googleOAuthService.exchangeCodeForUserInfo(code)).thenReturn(mockPayload);
        when(userRepository.findByEmailWithRelations(email)).thenReturn(Optional.of(owner));
        when(jwtService.generateToken(owner.getId(), owner.getUsername(), owner.getRole().name(), owner.getBusiness().getId(), owner.getBusiness().getName()))
            .thenReturn("mock-jwt-token");

        // Act
        AuthService.LoginResult result = authService.loginWithGoogle(code);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.token()).isEqualTo("mock-jwt-token");
        
        AuthResponse response = result.response();
        assertThat(response.getUserId()).isEqualTo(ownerId);
        assertThat(response.getRole()).isEqualTo(UserRole.OWNER);
        assertThat(response.getBusinessId()).isEqualTo(businessId);

        verify(activityLogService).logUserLogin(owner, business);
    }

    @Test
    @DisplayName("Should create new owner and generate JWT on first Google login")
    void loginWithGoogle_newOwner_success() {
        // Arrange
        String code = "google-auth-code";
        String email = "newowner@test.com";

        GoogleIdToken.Payload mockPayload = new GoogleIdToken.Payload();
        mockPayload.setEmail(email);

        UserEntity newOwner = UserEntity.builder()
            .username(email)
            .email(email)
            .role(UserRole.OWNER)
            .business(null)
            .build();
        newOwner.setId(UUID.randomUUID());

        when(googleOAuthService.exchangeCodeForUserInfo(code)).thenReturn(mockPayload);
        when(userRepository.findByEmailWithRelations(email)).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenReturn(newOwner);
        when(jwtService.generateToken(newOwner.getId(), newOwner.getUsername(), newOwner.getRole().name(), null, null))
            .thenReturn("mock-jwt-token");

        // Act
        AuthService.LoginResult result = authService.loginWithGoogle(code);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.token()).isEqualTo("mock-jwt-token");
        
        AuthResponse response = result.response();
        assertThat(response.getUserId()).isEqualTo(newOwner.getId());
        assertThat(response.getRole()).isEqualTo(UserRole.OWNER);
        assertThat(response.getBusinessId()).isNull(); // New owner shouldn't have business yet

        // Should save new user but not log login (business is null)
        verify(userRepository).save(any(UserEntity.class));
        verify(activityLogService, never()).logUserLogin(any(), any());
    }
}
