package com.orasa.backend.controller;

import com.orasa.backend.dto.auth.AuthResponse;
import com.orasa.backend.dto.common.ApiResponse;
import com.orasa.backend.dto.profile.ChangePasswordRequest;
import com.orasa.backend.dto.profile.UpdateProfileRequest;
import com.orasa.backend.security.AuthenticatedUser;
import com.orasa.backend.service.AuthService;
import com.orasa.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;
    private final AuthService authService;

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<AuthResponse>> updateMyProfile(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.updateProfile(authenticatedUser.userId(), request)
        ));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        authService.changePassword(authenticatedUser.userId(), request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
