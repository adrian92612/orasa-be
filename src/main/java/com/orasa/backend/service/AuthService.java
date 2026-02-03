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
import com.orasa.backend.domain.Branch;
import com.orasa.backend.domain.User;
import com.orasa.backend.dto.auth.AuthResponse;
import com.orasa.backend.dto.auth.StaffLoginRequest;
import com.orasa.backend.repository.UserRepository;
import com.orasa.backend.security.JwtService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {
  
  private final UserRepository userRepository;
  private final JwtService jwtService;
  private final AuthenticationManager authenticationManager;
  private final GoogleOAuthService googleOAuthService;

  public record LoginResult(String token, AuthResponse response) {}
  
  public LoginResult loginStaff(StaffLoginRequest request) {
    authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                request.getUsername(),
                request.getPassword()
            )
        );

    User user = userRepository.findByUsername(request.getUsername()).orElseThrow(() -> new UsernameNotFoundException("User not found"));

    List<UUID> branchIds = user.getBranches() != null 
    ? user.getBranches().stream().map(Branch::getId).toList()
    : List.of();

    String token = jwtService.generateToken(
      user.getId(),
      user.getUsername(),
      user.getRole().name(),
      user.getBusiness().getId(),
      branchIds
    );

    AuthResponse response = AuthResponse.builder()
      .userId(user.getId())
      .role(user.getRole())
      .businessId(user.getBusiness().getId())
      .branchIds(branchIds)
      .build();

    return new LoginResult(token, response);
  }

  public LoginResult loginWithGoogle(String code) {
    GoogleIdToken.Payload payload = googleOAuthService.exchangeCodeForUserInfo(code);
    
    String email = payload.getEmail();

    // Find existing user OR create new Owner
    User user = userRepository.findByEmail(email)
        .orElseGet(() -> createNewOwner(email));

    // Verify role is Owner (in case Staff tries to use Google login)
    if (user.getRole() != UserRole.OWNER) {
        throw new BadCredentialsException("Google login is only for business owners");
    }

    UUID businessId = user.getBusiness() != null ? user.getBusiness().getId() : null;
    List<UUID> branchIds = user.getBusiness() != null && user.getBranches() != null
        ? user.getBranches().stream().map(Branch::getId).toList()
        : List.of();

    String token = jwtService.generateToken(
        user.getId(),
        user.getUsername(),
        user.getRole().name(),
        businessId,
        branchIds
    );

    return new LoginResult(token, AuthResponse.builder()
        .userId(user.getId())
        .role(user.getRole())
        .businessId(businessId)
        .branchIds(branchIds)
        .build());
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
