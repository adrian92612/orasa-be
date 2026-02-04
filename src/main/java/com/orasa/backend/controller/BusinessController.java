package com.orasa.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orasa.backend.domain.Branch;
import com.orasa.backend.domain.User;
import com.orasa.backend.dto.business.BusinessResponse;
import com.orasa.backend.dto.business.CreateBusinessRequest;
import com.orasa.backend.dto.common.ApiResponse;
import com.orasa.backend.exception.ResourceNotFoundException;
import com.orasa.backend.repository.UserRepository;
import com.orasa.backend.security.AuthenticatedUser;
import com.orasa.backend.security.JwtService;
import com.orasa.backend.service.BusinessService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/businesses")
@RequiredArgsConstructor
public class BusinessController {

    private final BusinessService businessService;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    /**
     * Creates a new business with its first branch.
     * Only accessible by OWNER role users who don't have a business yet.
     * Automatically refreshes JWT with the new businessId.
     */
    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<BusinessResponse>> createBusiness(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody CreateBusinessRequest request,
            HttpServletResponse response
    ) {
        BusinessResponse businessResponse = businessService.createBusinessWithBranch(
                authenticatedUser.userId(),
                request
        );

        // Auto-refresh JWT with the new businessId
        refreshJwtCookie(authenticatedUser.userId(), response);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Business created successfully", businessResponse));
    }

    /**
     * Gets the current user's business.
     */
    @GetMapping("/me")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<BusinessResponse>> getMyBusiness(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        if (authenticatedUser.businessId() == null) {
            return ResponseEntity.ok(ApiResponse.success("No business found", null));
        }

        BusinessResponse business = businessService.getBusinessById(authenticatedUser.businessId());
        return ResponseEntity.ok(ApiResponse.success(business));
    }

    /**
     * Refreshes the JWT cookie with updated user claims from the database.
     * Called after business creation to include the new businessId.
     */
    private void refreshJwtCookie(UUID userId, HttpServletResponse response) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        UUID businessId = user.getBusiness() != null ? user.getBusiness().getId() : null;
        List<UUID> branchIds = user.getBranches() != null
                ? user.getBranches().stream().map(Branch::getId).toList()
                : List.of();

        String newToken = jwtService.generateToken(
                user.getId(),
                user.getUsername(),
                user.getRole().name(),
                businessId,
                branchIds
        );

        ResponseCookie cookie = ResponseCookie.from("token", newToken)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(jwtExpiration / 1000)
                .sameSite("None")
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
    }
}
