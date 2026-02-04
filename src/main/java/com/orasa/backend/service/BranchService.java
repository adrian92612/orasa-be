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
    private final ActivityLogService activityLogService;

    @Transactional
    public BranchResponse createBranch(UUID ownerId, UUID businessId, CreateBranchRequest request) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found"));
        
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));

        Branch branch = Branch.builder()
                .business(business)
                .name(request.getName())
                .address(request.getAddress())
                .phoneNumber(request.getPhoneNumber())
                .build();

        Branch saved = branchRepository.save(branch);

        owner.getBranches().add(saved);
        userRepository.save(owner);

        // Log branch creation
        activityLogService.logBranchCreated(owner, business, saved);

        return mapToResponse(saved);
    }

    public List<BranchResponse> getBranchesByBusiness(UUID businessId) {
        return branchRepository.findByBusinessId(businessId).stream()
                .map(this::mapToResponse)
                .toList();
    }

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

