package com.orasa.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orasa.backend.dto.branch.BranchResponse;
import com.orasa.backend.dto.branch.CreateBranchRequest;
import com.orasa.backend.dto.common.ApiResponse;
import com.orasa.backend.exception.BusinessException;
import com.orasa.backend.security.AuthenticatedUser;
import com.orasa.backend.service.BranchService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;

    /**
     * Creates a new branch for the owner's business.
     * Only accessible by OWNER role.
     */
    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<BranchResponse>> createBranch(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody CreateBranchRequest request
    ) {
        if (authenticatedUser.businessId() == null) {
            throw new BusinessException("Business must be created first");
        }

        BranchResponse branch = branchService.createBranch(
                authenticatedUser.userId(),
                authenticatedUser.businessId(),
                request
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Branch created successfully", branch));
    }

    /**
     * Gets all branches for the authenticated user's business.
     */
    @GetMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<List<BranchResponse>>> getMyBranches(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        if (authenticatedUser.businessId() == null) {
            throw new BusinessException("No business found");
        }

        List<BranchResponse> branches = branchService.getBranchesByBusiness(authenticatedUser.businessId());
        return ResponseEntity.ok(ApiResponse.success(branches));
    }

    /**
     * Gets a specific branch by ID.
     * Validates that the branch belongs to the user's business.
     */
    @GetMapping("/{branchId}")
    @PreAuthorize("hasRole('OWNER') or hasRole('STAFF')")
    public ResponseEntity<ApiResponse<BranchResponse>> getBranchById(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID branchId
    ) {
        BranchResponse branch = branchService.getBranchById(branchId);

        // Verify branch belongs to user's business
        if (!branch.getBusinessId().equals(authenticatedUser.businessId())) {
            throw new BusinessException("Branch does not belong to your business");
        }

        return ResponseEntity.ok(ApiResponse.success(branch));
    }
}
