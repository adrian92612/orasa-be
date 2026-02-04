package com.orasa.backend.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orasa.backend.domain.Branch;
import com.orasa.backend.domain.Business;
import com.orasa.backend.domain.User;
import com.orasa.backend.dto.business.BusinessResponse;
import com.orasa.backend.dto.business.CreateBusinessRequest;
import com.orasa.backend.exception.BusinessException;
import com.orasa.backend.exception.ResourceNotFoundException;
import com.orasa.backend.repository.BranchRepository;
import com.orasa.backend.repository.BusinessRepository;
import com.orasa.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BusinessService {

    private final BusinessRepository businessRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;

    /**
     * Creates a new business with its first branch atomically.
     * Used during onboarding to prevent "limbo state" (business without branch).
     * 
     * @param ownerId The authenticated owner's user ID
     * @param request Contains business name and first branch details
     * @return BusinessResponse with both businessId and firstBranchId
     */
    @Transactional
    public BusinessResponse createBusinessWithBranch(UUID ownerId, CreateBusinessRequest request) {
        // Verify owner exists and doesn't already have a business
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (owner.getBusiness() != null) {
            throw new BusinessException("User already has a business");
        }

        // Create the business
        Business business = Business.builder()
                .name(request.getName())
                .slug(generateSlug(request.getName()))
                .build();

        Business savedBusiness = businessRepository.save(business);

        // Create the first branch
        Branch branch = Branch.builder()
                .business(savedBusiness)
                .name(request.getBranch().getName())
                .address(request.getBranch().getAddress())
                .phoneNumber(request.getBranch().getPhoneNumber())
                .build();

        Branch savedBranch = branchRepository.save(branch);

        // Link business to owner
        owner.setBusiness(savedBusiness);
        owner.getBranches().add(savedBranch);
        userRepository.save(owner);

        return mapToResponse(savedBusiness, savedBranch.getId());
    }

    /**
     * Gets a business by ID.
     */
    public BusinessResponse getBusinessById(UUID businessId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));

        return mapToResponse(business, null);
    }

    /**
     * Generates a URL-friendly slug from the business name.
     */
    private String generateSlug(String name) {
        String baseSlug = name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        // Ensure uniqueness by appending a short random suffix
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 6);
        return baseSlug + "-" + uniqueSuffix;
    }

    private BusinessResponse mapToResponse(Business business, UUID firstBranchId) {
        return BusinessResponse.builder()
                .id(business.getId())
                .name(business.getName())
                .slug(business.getSlug())
                .freeSmsCredits(business.getFreeSmsCredits())
                .paidSmsCredits(business.getPaidSmsCredits())
                .subscriptionStatus(business.getSubscriptionStatus())
                .subscriptionStartDate(business.getSubscriptionStartDate())
                .subscriptionEndDate(business.getSubscriptionEndDate())
                .createdAt(business.getCreatedAt())
                .firstBranchId(firstBranchId)
                .build();
    }
}
