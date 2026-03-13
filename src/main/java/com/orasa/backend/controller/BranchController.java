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
import com.orasa.backend.dto.branch.UpdateBranchRequest;
import com.orasa.backend.dto.common.ApiResponse;
import com.orasa.backend.exception.BusinessException;
import com.orasa.backend.security.AuthenticatedUser;
import com.orasa.backend.service.BranchService;
import com.orasa.backend.common.UserRole;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/branches")
@RequiredArgsConstructor
@Slf4j
public class BranchController extends BaseController {

        private final BranchService branchService;

        @PostMapping
        @PreAuthorize("hasRole('OWNER')")
        public ResponseEntity<ApiResponse<BranchResponse>> createBranch(
                        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
                        @Valid @RequestBody CreateBranchRequest request) {
                validateBusinessExists(authenticatedUser);
                log.info("User {} creating branch for business {}", authenticatedUser.userId(),
                                authenticatedUser.businessId());

                BranchResponse branch = branchService.createBranch(
                                authenticatedUser.userId(),
                                authenticatedUser.businessId(),
                                request);

                log.info("Branch created successfully: {}", branch.getId());
                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.success("Branch created successfully", branch));
        }

        @GetMapping
        @PreAuthorize("hasRole('OWNER') or hasRole('STAFF')")
        public ResponseEntity<ApiResponse<List<BranchResponse>>> getMyBranches(
                        @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
                validateBusinessExists(authenticatedUser);
                log.info("Fetching branches for user {} (Role: {})", authenticatedUser.userId(),
                                authenticatedUser.role());

                List<BranchResponse> branches;
                if (authenticatedUser.role() == UserRole.OWNER) {
                        branches = branchService.getBranchesByBusiness(authenticatedUser.businessId());
                } else {
                        branches = branchService.getBranchesForUser(authenticatedUser.userId(),
                                        authenticatedUser.businessId());
                }

                log.info("Found {} branches for user {}", branches.size(), authenticatedUser.userId());
                return ResponseEntity.ok(ApiResponse.success(branches));
        }

        @PutMapping("/{branchId}")
        @PreAuthorize("hasRole('OWNER')")
        public ResponseEntity<ApiResponse<BranchResponse>> updateBranch(
                        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
                        @PathVariable UUID branchId,
                        @Valid @RequestBody UpdateBranchRequest request) {
                validateBusinessExists(authenticatedUser);
                log.info("User {} updating branch {}", authenticatedUser.userId(), branchId);

                BranchResponse branch = branchService.updateBranch(
                                authenticatedUser.userId(),
                                branchId,
                                authenticatedUser.businessId(),
                                request);

                log.info("Branch {} updated successfully", branchId);
                return ResponseEntity.ok(ApiResponse.success("Branch updated successfully", branch));
        }

        @GetMapping("/{branchId}")
        @PreAuthorize("hasRole('OWNER') or hasRole('STAFF')")
        public ResponseEntity<ApiResponse<BranchResponse>> getBranchById(
                        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
                        @PathVariable UUID branchId) {
                log.debug("Fetching branch {}", branchId);
                BranchResponse branch = branchService.getBranchById(branchId, authenticatedUser.businessId());

                if (!branch.getBusinessId().equals(authenticatedUser.businessId())) {
                        log.warn("User {} attempted to access unauthorized branch {}", authenticatedUser.userId(),
                                        branchId);
                        throw new BusinessException("Branch does not belong to your business");
                }

                return ResponseEntity.ok(ApiResponse.success(branch));
        }

        @DeleteMapping("/{branchId}")
        @PreAuthorize("hasRole('OWNER')")
        public ResponseEntity<ApiResponse<Void>> deleteBranch(
                        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
                        @PathVariable UUID branchId) {
                validateBusinessExists(authenticatedUser);
                log.info("User {} deleting branch {}", authenticatedUser.userId(), branchId);

                branchService.deleteBranch(authenticatedUser.userId(), branchId, authenticatedUser.businessId());

                return ResponseEntity.ok(ApiResponse.success("Branch deleted successfully"));
        }


}
