package com.orasa.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orasa.backend.dto.common.ApiResponse;
import com.orasa.backend.dto.staff.ChangePasswordRequest;
import com.orasa.backend.dto.staff.CreateStaffRequest;
import com.orasa.backend.dto.staff.StaffResponse;
import com.orasa.backend.dto.staff.UpdateStaffRequest;
import com.orasa.backend.exception.BusinessException;
import com.orasa.backend.security.AuthenticatedUser;
import com.orasa.backend.service.StaffService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/staff")
@RequiredArgsConstructor
public class StaffController {

    private final StaffService staffService;

    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<StaffResponse>> createStaff(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody CreateStaffRequest request
    ) {
        validateBusinessExists(authenticatedUser);

        StaffResponse staff = staffService.createStaff(
                authenticatedUser.businessId(),
                request
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Staff member created successfully", staff));
    }

    @PutMapping("/{staffId}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<StaffResponse>> updateStaff(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID staffId,
            @Valid @RequestBody UpdateStaffRequest request
    ) {
        validateBusinessExists(authenticatedUser);

        StaffResponse staff = staffService.updateStaff(
                staffId,
                authenticatedUser.businessId(),
                request
        );

        return ResponseEntity.ok(ApiResponse.success("Staff member updated successfully", staff));
    }

    @GetMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<List<StaffResponse>>> getMyStaff(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        validateBusinessExists(authenticatedUser);

        List<StaffResponse> staffList = staffService.getStaffByBusiness(authenticatedUser.businessId());
        return ResponseEntity.ok(ApiResponse.success(staffList));
    }

    @GetMapping("/{staffId}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<StaffResponse>> getStaffById(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID staffId
    ) {
        validateBusinessExists(authenticatedUser);

        StaffResponse staff = staffService.getStaffMember(staffId, authenticatedUser.businessId());
        return ResponseEntity.ok(ApiResponse.success(staff));
    }

    @DeleteMapping("/{staffId}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<Void>> deleteStaff(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID staffId
    ) {
        validateBusinessExists(authenticatedUser);

        staffService.deleteStaff(staffId, authenticatedUser.businessId());
        return ResponseEntity.ok(ApiResponse.success("Staff member deleted successfully"));
    }

    @PostMapping("/change-password")
    @PreAuthorize("hasRole('OWNER') or hasRole('STAFF')")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        staffService.changePassword(authenticatedUser.userId(), request);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully"));
    }

    private void validateBusinessExists(AuthenticatedUser user) {
        if (user.businessId() == null) {
            throw new BusinessException("Business must be created first");
        }
    }
}
