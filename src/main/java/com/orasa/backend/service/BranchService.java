package com.orasa.backend.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orasa.backend.domain.Branch;
import com.orasa.backend.domain.Business;
import com.orasa.backend.domain.User;
import com.orasa.backend.dto.branch.BranchResponse;
import com.orasa.backend.dto.branch.CreateBranchRequest;
import com.orasa.backend.exception.ResourceNotFoundException;
import com.orasa.backend.repository.BranchRepository;
import com.orasa.backend.repository.BusinessRepository;
import com.orasa.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BranchService {

    private final BranchRepository branchRepository;
    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;

    /**
     * Creates a new branch for the owner's business.
     * The businessId is taken from the authenticated user's JWT.
     */
    @Transactional
    public BranchResponse createBranch(UUID ownerId, UUID businessId, CreateBranchRequest request) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));

        Branch branch = Branch.builder()
                .business(business)
                .name(request.getName())
                .address(request.getAddress())
                .phoneNumber(request.getPhoneNumber())
                .build();

        Branch saved = branchRepository.save(branch);

        // Add branch to owner's accessible branches
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found"));
        owner.getBranches().add(saved);
        userRepository.save(owner);

        return mapToResponse(saved);
    }

    /**
     * Gets all branches for a business.
     */
    public List<BranchResponse> getBranchesByBusiness(UUID businessId) {
        return branchRepository.findByBusinessId(businessId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Gets a specific branch by ID.
     */
    public BranchResponse getBranchById(UUID branchId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));

        return mapToResponse(branch);
    }

    private BranchResponse mapToResponse(Branch branch) {
        return BranchResponse.builder()
                .id(branch.getId())
                .businessId(branch.getBusiness().getId())
                .name(branch.getName())
                .address(branch.getAddress())
                .phoneNumber(branch.getPhoneNumber())
                .createdAt(branch.getCreatedAt())
                .updatedAt(branch.getUpdatedAt())
                .build();
    }
}
