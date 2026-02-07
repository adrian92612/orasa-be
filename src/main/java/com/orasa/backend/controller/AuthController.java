package com.orasa.backend.controller;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import com.orasa.backend.domain.User;
import com.orasa.backend.dto.auth.AuthResponse;
import com.orasa.backend.dto.auth.StaffLoginRequest;
import com.orasa.backend.dto.common.ApiResponse;
import com.orasa.backend.repository.UserRepository;
import com.orasa.backend.security.AuthenticatedUser;
import com.orasa.backend.service.AuthService;
import com.orasa.backend.service.GoogleOAuthService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController extends BaseController {
  
  private final AuthService authService;
  private final GoogleOAuthService googleOAuthService;
  private final UserRepository userRepository;

  @Value("${jwt.expiration}")
  private long jwtExpiration;

  @Value("${app.frontend-url}")
  private String frontendUrl;

  @PostMapping("/staff/login")
  public ResponseEntity<ApiResponse<AuthResponse>> loginStaff(
    @Valid @RequestBody StaffLoginRequest request,
    HttpServletResponse response
  ) {
    AuthService.LoginResult result = authService.loginStaff(request);

    addTokenCookie(response, result.token());
    return ResponseEntity.ok(ApiResponse.success(result.response()));
  }

  @PostMapping("/logout")
  public ResponseEntity<ApiResponse<Void>> logout(HttpServletResponse response) {
    ResponseCookie cookie = ResponseCookie.from("token", "")
        .httpOnly(true)
        .secure(true)
        .path("/")
        .maxAge(0)
        .sameSite("None")
        .build();

    response.addHeader("Set-Cookie", cookie.toString());
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  @GetMapping("/me")
  public ResponseEntity<ApiResponse<AuthResponse>> getCurrentUser(
    @AuthenticationPrincipal AuthenticatedUser user
  ) {
    User currentUser = userRepository.findByIdWithRelations(user.userId())
        .orElseThrow(() -> new com.orasa.backend.exception.ResourceNotFoundException("User not found"));

    List<AuthResponse.BranchInfo> branchInfos = currentUser.getBranches().stream()
        .map(b -> AuthResponse.BranchInfo.builder()
            .id(b.getId())
            .name(b.getName())
            .build())
        .toList();

    AuthResponse userData = AuthResponse.builder()
      .userId(currentUser.getId())
      .username(currentUser.getUsername())
      .businessId(currentUser.getBusiness() != null ? currentUser.getBusiness().getId() : null)
      .businessName(currentUser.getBusiness() != null ? currentUser.getBusiness().getName() : null)
      .role(currentUser.getRole())
      .branches(branchInfos)
      .build();
    return ResponseEntity.ok(ApiResponse.success(userData));
  }

  // Step 1: Frontend calls this to start Google login
  @GetMapping("/google")
  public void redirectToGoogle(HttpServletResponse response) throws IOException {
    String authUrl = googleOAuthService.getAuthorizationUrl();
    response.sendRedirect(authUrl);
  }

  // Step 2: Google redirects back here with code
  @GetMapping("/google/callback")
  public void handleGoogleCallback(
      @RequestParam("code") String code,
      HttpServletResponse response
  ) throws IOException {
    AuthService.LoginResult result = authService.loginWithGoogle(code);
    
    addTokenCookie(response, result.token());
    
    // Redirect to frontend - dashboard or onboarding based on businessId
    String redirectPath = result.response().getBusinessId() != null 
        ? "/dashboard" 
        : "/onboarding";
    
    response.sendRedirect(frontendUrl + redirectPath);
  }

  private void addTokenCookie(HttpServletResponse response, String token) {
    ResponseCookie cookie = ResponseCookie.from("token", token)
        .httpOnly(true)
        .secure(true)
        .path("/")
        .maxAge(jwtExpiration / 1000)
        .sameSite("None")
        .build();
    response.addHeader("Set-Cookie", cookie.toString());
  }
}



