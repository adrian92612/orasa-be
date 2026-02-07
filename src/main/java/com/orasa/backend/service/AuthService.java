package com.orasa.backend.service;

import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.orasa.backend.common.UserRole;
import com.orasa.backend.domain.User;
import com.orasa.backend.dto.auth.AuthResponse;
import com.orasa.backend.dto.auth.StaffLoginRequest;
import com.orasa.backend.repository.UserRepository;
import com.orasa.backend.exception.BusinessException;
import com.orasa.backend.exception.ResourceNotFoundException;
import com.orasa.backend.security.JwtService;
import com.orasa.backend.dto.profile.ChangePasswordRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {
  
  private final UserRepository userRepository;
  private final JwtService jwtService;
  private final AuthenticationManager authenticationManager;
  private final GoogleOAuthService googleOAuthService;
  private final PasswordEncoder passwordEncoder;
  private final ActivityLogService activityLogService;

  public record LoginResult(String token, AuthResponse response) {}
  
  public LoginResult loginStaff(StaffLoginRequest request) {
    authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                request.getUsername(),
                request.getPassword()
            )
        );

    User user = userRepository.findByUsernameWithRelations(request.getUsername()).orElseThrow(() -> new UsernameNotFoundException("User not found"));

    String token = jwtService.generateToken(
      user.getId(),
      user.getUsername(),
      user.getRole().name()
    );

    List<AuthResponse.BranchInfo> branchInfos = user.getBranches() != null
      ? user.getBranches().stream()
          .map(b -> AuthResponse.BranchInfo.builder()
              .id(b.getId())
              .name(b.getName())
              .build())
          .toList()
      : List.of();

    AuthResponse response = AuthResponse.builder()
      .userId(user.getId())
      .username(user.getUsername())
      .role(user.getRole())
      .businessId(user.getBusiness().getId())
      .businessName(user.getBusiness().getName())
      .branches(branchInfos)
      .build();

    return new LoginResult(token, response);
  }

  public LoginResult loginWithGoogle(String code) {
    GoogleIdToken.Payload payload = googleOAuthService.exchangeCodeForUserInfo(code);
    
    String email = payload.getEmail();

    User user = userRepository.findByEmailWithRelations(email)
        .orElseGet(() -> createNewOwner(email));

    if (user.getRole() != UserRole.OWNER) {
        throw new BadCredentialsException("Google login is only for business owners");
    }

    UUID businessId = user.getBusiness() != null ? user.getBusiness().getId() : null;

    String token = jwtService.generateToken(
        user.getId(),
        user.getUsername(),
        user.getRole().name()
    );

    List<AuthResponse.BranchInfo> branchInfos = user.getBusiness() != null && user.getBranches() != null
        ? user.getBranches().stream()
            .map(b -> AuthResponse.BranchInfo.builder()
                .id(b.getId())
                .name(b.getName())
                .build())
            .toList()
        : List.of();

    return new LoginResult(token, AuthResponse.builder()
        .userId(user.getId())
        .username(user.getUsername())
        .role(user.getRole())
        .businessId(businessId)
        .businessName(user.getBusiness() != null ? user.getBusiness().getName() : null)
        .branches(branchInfos)
        .build());
  }

  public void changePassword(UUID userId, ChangePasswordRequest request) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    if (user.getPasswordHash() != null && !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
        throw new BusinessException("Current password is incorrect");
    }

    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    user.setMustChangePassword(false); 
    userRepository.save(user);
    
    activityLogService.logPasswordChanged(user, user.getBusiness());
  }

  private User createNewOwner(String email) {
    User owner = User.builder()
        .email(email)
        .username(email)
        .role(UserRole.OWNER)
        .business(null)
        .build();
    return userRepository.save(owner);
  }
}
