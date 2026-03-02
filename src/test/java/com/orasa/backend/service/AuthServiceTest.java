package com.orasa.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.orasa.backend.common.UserRole;
import com.orasa.backend.domain.BusinessEntity;
import com.orasa.backend.domain.UserEntity;
import com.orasa.backend.dto.auth.AuthResponse;
import com.orasa.backend.dto.auth.StaffLoginRequest;
import com.orasa.backend.dto.profile.ChangePasswordRequest;
import com.orasa.backend.exception.BusinessException;
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

    // ─── loginStaff ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("loginStaff")
    class LoginStaffTests {

        @Test
        @DisplayName("Should successfully login staff and generate JWT")
        void loginStaff_success() {
            StaffLoginRequest request = new StaffLoginRequest();
            request.setUsername("staffuser");
            request.setPassword("password");

            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(new UsernamePasswordAuthenticationToken("staffuser", "password"));
            when(userRepository.findByUsernameWithRelations("staffuser")).thenReturn(Optional.of(staff));
            when(jwtService.generateToken(staff.getId(), staff.getUsername(), staff.getRole().name(),
                    staff.getBusiness().getId(), staff.getBusiness().getName()))
                    .thenReturn("mock-jwt-token");

            AuthService.LoginResult result = authService.loginStaff(request);

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
        @DisplayName("Should propagate BadCredentialsException on wrong password")
        void loginStaff_wrongPassword_throwsBadCredentials() {
            StaffLoginRequest request = new StaffLoginRequest();
            request.setUsername("staffuser");
            request.setPassword("wrongpassword");

            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            assertThatThrownBy(() -> authService.loginStaff(request))
                    .isInstanceOf(BadCredentialsException.class);

            verify(userRepository, never()).findByUsernameWithRelations(any());
            verify(activityLogService, never()).logUserLogin(any(), any());
        }
    }

    // ─── loginWithGoogle ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("loginWithGoogle")
    class LoginWithGoogleTests {

        @Test
        @DisplayName("Should login via Google and generate JWT for existing owner")
        void loginWithGoogle_existingOwner_success() {
            String code = "google-auth-code";
            String email = "owner@test.com";

            GoogleIdToken.Payload mockPayload = new GoogleIdToken.Payload();
            mockPayload.setEmail(email);

            when(googleOAuthService.exchangeCodeForUserInfo(code)).thenReturn(mockPayload);
            when(userRepository.findByEmailWithRelations(email)).thenReturn(Optional.of(owner));
            when(jwtService.generateToken(owner.getId(), owner.getUsername(), owner.getRole().name(),
                    owner.getBusiness().getId(), owner.getBusiness().getName()))
                    .thenReturn("mock-jwt-token");

            AuthService.LoginResult result = authService.loginWithGoogle(code);

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
            when(jwtService.generateToken(newOwner.getId(), newOwner.getUsername(), newOwner.getRole().name(), null,
                    null))
                    .thenReturn("mock-jwt-token");

            AuthService.LoginResult result = authService.loginWithGoogle(code);

            assertThat(result).isNotNull();
            assertThat(result.token()).isEqualTo("mock-jwt-token");

            AuthResponse response = result.response();
            assertThat(response.getUserId()).isEqualTo(newOwner.getId());
            assertThat(response.getRole()).isEqualTo(UserRole.OWNER);
            assertThat(response.getBusinessId()).isNull(); // New owner has no business yet

            verify(userRepository).save(any(UserEntity.class));
            verify(activityLogService, never()).logUserLogin(any(), any());
        }

        @Test
        @DisplayName("Should throw BadCredentialsException when a STAFF user tries to use Google login")
        void loginWithGoogle_staffRole_throwsBadCredentials() {
            String code = "google-auth-code";
            String email = "staffuser@test.com";

            UserEntity staffWithEmail = UserEntity.builder()
                    .username(email)
                    .email(email)
                    .role(UserRole.STAFF)
                    .business(business)
                    .build();
            staffWithEmail.setId(UUID.randomUUID());

            GoogleIdToken.Payload mockPayload = new GoogleIdToken.Payload();
            mockPayload.setEmail(email);

            when(googleOAuthService.exchangeCodeForUserInfo(code)).thenReturn(mockPayload);
            when(userRepository.findByEmailWithRelations(email)).thenReturn(Optional.of(staffWithEmail));

            assertThatThrownBy(() -> authService.loginWithGoogle(code))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessageContaining("Google login is only for business owners");
        }
    }

    // ─── logout ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("logout")
    class LogoutTests {

        @Test
        @DisplayName("Should log user logout when user has a business")
        void logout_withBusiness_logsLogout() {
            when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));

            authService.logout(ownerId);

            verify(activityLogService).logUserLogout(owner, business);
        }

        @Test
        @DisplayName("Should do nothing when userId is null")
        void logout_nullUserId_doesNothing() {
            authService.logout(null);

            verify(userRepository, never()).findById(any());
            verify(activityLogService, never()).logUserLogout(any(), any());
        }
    }

    // ─── changePassword ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("changePassword")
    class ChangePasswordTests {

        @Test
        @DisplayName("Should change password when current password is correct")
        void changePassword_correctCurrentPassword_savesNewHash() {
            String currentPassword = "oldPassword";
            String newPassword = "newPassword123";
            String hashedOld = "hashedOld";
            String hashedNew = "hashedNew";

            staff.setPasswordHash(hashedOld);
            when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
            when(passwordEncoder.matches(currentPassword, hashedOld)).thenReturn(true);
            when(passwordEncoder.encode(newPassword)).thenReturn(hashedNew);

            authService.changePassword(staffId, new ChangePasswordRequest(currentPassword, newPassword));

            assertThat(staff.getPasswordHash()).isEqualTo(hashedNew);
            verify(userRepository).save(staff);
            verify(activityLogService).logPasswordChanged(staff, business);
        }

        @Test
        @DisplayName("Should throw BusinessException when current password is incorrect")
        void changePassword_wrongCurrentPassword_throwsException() {
            String hashedOld = "hashedOld";
            staff.setPasswordHash(hashedOld);
            when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
            when(passwordEncoder.matches("wrongPassword", hashedOld)).thenReturn(false);

            assertThatThrownBy(
                    () -> authService.changePassword(staffId, new ChangePasswordRequest("wrongPassword", "new123")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Current password is incorrect");

            verify(userRepository, never()).save(any());
        }
    }
}
