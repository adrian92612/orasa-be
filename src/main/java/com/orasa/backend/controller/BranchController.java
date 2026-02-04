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

import com.orasa.backend.dto.branch.BranchResponse;
import com.orasa.backend.dto.branch.CreateBranchRequest;
import com.orasa.backend.dto.common.ApiResponse;
import com.orasa.backend.dto.service.AssignServiceToBranchRequest;
import com.orasa.backend.dto.service.BranchServiceResponse;
import com.orasa.backend.exception.BusinessException;
import com.orasa.backend.security.AuthenticatedUser;
import com.orasa.backend.service.BranchService;
import com.orasa.backend.service.BranchServiceService;
import com.orasa.backend.common.RequiresActiveSubscription;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/branches")
@RequiredArgsConstructor
public class BranchController extends BaseController {

    private final BranchService branchService;
    private final BranchServiceService branchServiceService;

    @PostMapping
    @RequiresActiveSubscription
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

    @GetMapping("/{branchId}")
    @PreAuthorize("hasRole('OWNER') or hasRole('STAFF')")
    public ResponseEntity<ApiResponse<BranchResponse>> getBranchById(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID branchId
    ) {
        BranchResponse branch = branchService.getBranchById(branchId);

        if (!branch.getBusinessId().equals(authenticatedUser.businessId())) {
            throw new BusinessException("Branch does not belong to your business");
        }

        return ResponseEntity.ok(ApiResponse.success(branch));
    }

    @PostMapping("/{branchId}/services")
    @RequiresActiveSubscription
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<BranchServiceResponse>> assignServiceToBranch(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID branchId,
            @Valid @RequestBody AssignServiceToBranchRequest request
    ) {
        validateBusinessExists(authenticatedUser);

        BranchServiceResponse branchService = branchServiceService.assignServiceToBranch(
                branchId,
                authenticatedUser.businessId(),
                request
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Service assigned to branch successfully", branchService));
    }

    @GetMapping("/{branchId}/services")
    @PreAuthorize("hasRole('OWNER') or hasRole('STAFF')")
    public ResponseEntity<ApiResponse<List<BranchServiceResponse>>> getServicesByBranch(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID branchId
    ) {
        BranchResponse branch = branchService.getBranchById(branchId);
        if (!branch.getBusinessId().equals(authenticatedUser.businessId())) {
            throw new BusinessException("Branch does not belong to your business");
        }

        List<BranchServiceResponse> services = branchServiceService.getServicesByBranch(branchId);
        return ResponseEntity.ok(ApiResponse.success(services));
    }

    @PutMapping("/{branchId}/services/{branchServiceId}")
    @RequiresActiveSubscription
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<BranchServiceResponse>> updateBranchService(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID branchId,
            @PathVariable UUID branchServiceId,
            @Valid @RequestBody AssignServiceToBranchRequest request
    ) {
        validateBusinessExists(authenticatedUser);

        BranchServiceResponse branchService = branchServiceService.updateBranchService(
                branchServiceId,
                authenticatedUser.businessId(),
                request
        );

        return ResponseEntity.ok(ApiResponse.success("Branch service updated successfully", branchService));
    }

    @DeleteMapping("/{branchId}/services/{branchServiceId}")
    @RequiresActiveSubscription
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<Void>> removeServiceFromBranch(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID branchId,
            @PathVariable UUID branchServiceId
    ) {
        validateBusinessExists(authenticatedUser);

        branchServiceService.removeServiceFromBranch(branchServiceId, authenticatedUser.businessId());
        return ResponseEntity.ok(ApiResponse.success("Service removed from branch successfully"));
    }
}

