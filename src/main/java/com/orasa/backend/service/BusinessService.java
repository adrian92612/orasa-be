package com.orasa.backend.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orasa.backend.domain.BranchEntity;
import com.orasa.backend.domain.BusinessEntity;
import com.orasa.backend.domain.UserEntity;
import com.orasa.backend.dto.business.BusinessResponse;
import com.orasa.backend.dto.business.CreateBusinessRequest;
import com.orasa.backend.dto.business.UpdateBusinessRequest;
import com.orasa.backend.exception.BusinessException;
import com.orasa.backend.common.SubscriptionStatus;
import com.orasa.backend.exception.ResourceNotFoundException;
import com.orasa.backend.repository.BranchRepository;
import com.orasa.backend.repository.BusinessRepository;
import com.orasa.backend.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.orasa.backend.dto.activity.FieldChange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class BusinessService {

    private final BusinessRepository businessRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;
    private final ActivityLogService activityLogService;

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
        UserEntity owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (owner.getBusiness() != null) {
            throw new BusinessException("User already has a business");
        }

        // Create the business
        BusinessEntity business = BusinessEntity.builder()
                .name(request.getName())
                .slug(generateSlug(request.getName()))
                .build();

        BusinessEntity savedBusiness = businessRepository.save(business);

        // Create the first branch
        BranchEntity branch = BranchEntity.builder()
                .business(savedBusiness)
                .name(request.getBranch().getName())
                .address(request.getBranch().getAddress())
                .phoneNumber(request.getBranch().getPhoneNumber())
                .build();

        BranchEntity savedBranch = branchRepository.save(branch);



        // Link business to owner
        owner.setBusiness(savedBusiness);
        owner.getBranches().add(savedBranch);
        userRepository.save(owner);

        activityLogService.logBusinessCreated(owner, savedBusiness);
        activityLogService.logBranchCreated(owner,savedBusiness, savedBranch);

        return mapToResponse(savedBusiness, savedBranch.getId());
    }

    /**
     * Gets a business by ID.
     */
    @Transactional
    public BusinessResponse getBusinessById(UUID businessId) {
        BusinessEntity business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));
        
        subscriptionService.checkAndRefreshCredits(business);

        return mapToResponse(business, null);
    }

    @Transactional
    public BusinessResponse updateBusiness(UUID businessId, UpdateBusinessRequest request) {
        BusinessEntity business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));
        
        String beforeName = business.getName();
        
        business.setName(request.name());
        business.setSlug(generateSlug(request.name()));
        
        BusinessEntity savedBusiness = businessRepository.save(business);
        
        UserEntity actor = getCurrentUser();
        
        if (!beforeName.equals(savedBusiness.getName())) {
            List<FieldChange> changes = List.of(new FieldChange("Business Name", beforeName, savedBusiness.getName()));
            activityLogService.logBusinessUpdated(actor, savedBusiness, FieldChange.toJson(changes));
        }
        
        return mapToResponse(savedBusiness, null);
    }

    @Transactional
    public Page<BusinessResponse> getAllBusinesses(String search, SubscriptionStatus status, Pageable pageable) {
        Page<BusinessEntity> page;
        
        if (status != null) {
            if (search != null && !search.isBlank()) {
                page = businessRepository.findByNameContainingIgnoreCaseAndSubscriptionStatus(search, status, pageable);
            } else {
                page = businessRepository.findBySubscriptionStatus(status, pageable);
            }
        } else {
            if (search != null && !search.isBlank()) {
                page = businessRepository.findByNameContainingIgnoreCase(search, pageable);
            } else {
                page = businessRepository.findAll(pageable);
            }
        }
        
        return page.map(business -> {
                    subscriptionService.checkAndRefreshCredits(business);
                    return mapToResponse(business, null);
                });
    }



    public UUID getCurrentUserBusinessId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
             throw new BusinessException("User not authenticated");
        }
        
        String username = authentication.getName();
        UserEntity user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            
        if (user.getBusiness() == null) {
            throw new ResourceNotFoundException("User does not have a business");
        }
        return user.getBusiness().getId();
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

    private BusinessResponse mapToResponse(BusinessEntity business, UUID firstBranchId) {
        return BusinessResponse.builder()
                .id(business.getId())
                .name(business.getName())
                .slug(business.getSlug())
                .freeSmsCredits(business.getFreeSmsCredits())
                .paidSmsCredits(business.getPaidSmsCredits())
                .subscriptionStatus(business.getSubscriptionStatus())
                .subscriptionStartDate(business.getSubscriptionStartDate())
                .subscriptionEndDate(business.getSubscriptionEndDate())
                .nextCreditResetDate(business.getNextCreditResetDate())
                .createdAt(business.getCreatedAt())
                .firstBranchId(firstBranchId)
                .build();
    }

    private UserEntity getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
             throw new BusinessException("User not authenticated");
        }
        return userRepository.findByUsername(authentication.getName())
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
