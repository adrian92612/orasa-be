package com.orasa.backend.controller;

import java.io.IOException;

import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.orasa.backend.dto.auth.AuthResponse;
import com.orasa.backend.dto.auth.StaffLoginRequest;
import com.orasa.backend.dto.common.ApiResponse;
import com.orasa.backend.security.AuthenticatedUser;
import com.orasa.backend.service.AuthService;
import com.orasa.backend.service.GoogleOAuthService;
import com.orasa.backend.config.OrasaProperties;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController extends BaseController {
  
  private final AuthService authService;
  private final GoogleOAuthService googleOAuthService;
  private final OrasaProperties orasaProperties;

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
  public ResponseEntity<ApiResponse<Void>> logout(
      @AuthenticationPrincipal AuthenticatedUser user,
      HttpServletResponse response
  ) {
    if (user != null) {
        authService.logout(user.userId());
    }

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
    if (user == null) {
        return ResponseEntity.status(401).body(ApiResponse.error("User not authenticated"));
    }

    AuthResponse userData = authService.getCurrentUser(user.userId());
    return ResponseEntity.ok(ApiResponse.success(userData));
  }

  @GetMapping("/google")
  public void redirectToGoogle(HttpServletResponse response) throws IOException {
    String authUrl = googleOAuthService.getAuthorizationUrl();
    response.sendRedirect(authUrl);
  }
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
    
    response.sendRedirect(orasaProperties.getApp().getFrontendUrl() + redirectPath);
  }

  private void addTokenCookie(HttpServletResponse response, String token) {
    ResponseCookie cookie = ResponseCookie.from("token", token)
        .httpOnly(true)
        .secure(true)
        .path("/")
        .maxAge(orasaProperties.getJwt().getExpiration() / 1000)
        .sameSite("None")
        .build();
    response.addHeader("Set-Cookie", cookie.toString());
  }
}



