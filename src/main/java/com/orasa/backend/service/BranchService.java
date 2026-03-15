package com.orasa.backend.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.orasa.backend.common.CacheName;
import org.springframework.transaction.annotation.Transactional;

import com.orasa.backend.common.UserRole;
import com.orasa.backend.config.CacheBusinessId;
import com.orasa.backend.domain.BranchEntity;
import com.orasa.backend.domain.BusinessEntity;
import com.orasa.backend.domain.UserEntity;

import com.orasa.backend.dto.activity.FieldChange;
import com.orasa.backend.dto.branch.BranchResponse;
import com.orasa.backend.dto.branch.CreateBranchRequest;
import com.orasa.backend.dto.branch.UpdateBranchRequest;

import com.orasa.backend.exception.ResourceNotFoundException;
import com.orasa.backend.exception.BusinessException;
import com.orasa.backend.common.utils.SanitizationUtils;
import com.orasa.backend.repository.BranchRepository;
import com.orasa.backend.repository.BusinessRepository;

import com.orasa.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class BranchService {

    private final BranchRepository branchRepository;
    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;
    private final CacheService cacheService;

    @Transactional
    public BranchResponse createBranch(UUID ownerId, UUID businessId, CreateBranchRequest request) {
        log.info("Creating new branch '{}' for business {}", request.getName(), businessId);
        UserEntity owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found"));

        BusinessEntity business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));

        BranchEntity branch = BranchEntity.builder()
                .business(business)
                .name(SanitizationUtils.sanitizeName(request.getName()))
                .address(request.getAddress())
                .phoneNumber(request.getPhoneNumber())
                .build();

        BranchEntity saved = branchRepository.save(branch);

        if (request.getStaffIds() != null && !request.getStaffIds().isEmpty()) {
            List<UserEntity> usersToAdd = userRepository.findAllById(request.getStaffIds());
            for (UserEntity user : usersToAdd) {
                if (user.getBusiness().getId().equals(businessId)) {
                    user.getBranches().add(saved);
                    // Maintain bidirectional if needed, but safe to just save owner
                    saved.getStaff().add(user); // Keep in sync in memory
                }
            }
            userRepository.saveAll(usersToAdd);
        }

        owner.getBranches().add(saved);
        userRepository.save(owner);

        // Log branch creation
        activityLogService.logBranchCreated(owner, business, saved);

        log.info("Branch created successfully: {}", saved.getId());
        cacheService.evictAll(CacheName.BRANCHES, businessId);
        cacheService.evictAll(CacheName.USER_BRANCHES, businessId);
        cacheService.evictAll(CacheName.SERVICES, businessId);
        cacheService.evictAll(CacheName.BUSINESS_STAFF, businessId);
        cacheService.evictAll(CacheName.STAFF, businessId);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheName.BRANCHES, keyGenerator = "businessKeyGenerator")
    public List<BranchResponse> getBranchesByBusiness(@CacheBusinessId UUID businessId) {
        List<BranchEntity> branches = branchRepository.findByBusinessId(businessId);
        return branches.stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheName.USER_BRANCHES, keyGenerator = "businessKeyGenerator")
    public List<BranchResponse> getBranchesForUser(UUID userId, @CacheBusinessId UUID businessId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return user.getBranches().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public BranchResponse updateBranch(UUID userId, UUID branchId, UUID businessId, UpdateBranchRequest request) {
        log.info("Updating branch {} for business {}", branchId, businessId);
        UserEntity actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        BranchEntity branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));

        if (!branch.getBusiness().getId().equals(businessId)) {
            log.warn("Business mismatch: Branch {} does not belong to business {}", branchId, businessId);
            throw new ResourceNotFoundException("Branch not found in your business");
        }

        List<FieldChange> changes = new ArrayList<>();

        // Check Name change
        String sanitizedName = SanitizationUtils.sanitizeName(request.getName());
        if (!branch.getName().equals(sanitizedName)) {
            changes.add(FieldChange.builder()
                    .field("Name")
                    .before(branch.getName())
                    .after(sanitizedName)
                    .build());
            branch.setName(sanitizedName);
        }

        // Check Address change
        String oldAddress = branch.getAddress();
        String newAddress = request.getAddress();
        String addressToSet = (newAddress == null || newAddress.trim().isEmpty()) ? null : newAddress.trim();

        if (!Objects.equals(oldAddress, addressToSet)) {
            changes.add(FieldChange.builder()
                    .field("Address")
                    .before(oldAddress != null ? oldAddress : "") // Log "empty" if previously null
                    .after(addressToSet != null ? addressToSet : "") // Log "empty" if cleared
                    .build());
            branch.setAddress(addressToSet);
        }

        // Check Phone change
        String oldPhone = branch.getPhoneNumber();
        String newPhone = request.getPhoneNumber();
        String phoneToSet = (newPhone == null || newPhone.trim().isEmpty()) ? null : newPhone.trim();

        if (!Objects.equals(oldPhone, phoneToSet)) {
            changes.add(FieldChange.builder()
                    .field("Phone Number")
                    .before(oldPhone != null ? oldPhone : "")
                    .after(phoneToSet != null ? phoneToSet : "")
                    .build());
            branch.setPhoneNumber(phoneToSet);
        }

        // Check Staff Assignment changes
        if (request.getStaffIds() != null) {
            Set<UUID> newStaffIds = request.getStaffIds();
            Set<UserEntity> currentStaff = branch.getStaff();

            // Identify users to remove
            List<UserEntity> toRemove = currentStaff.stream()
                    .filter(staff -> !newStaffIds.contains(staff.getId()))
                    .collect(Collectors.toList());

            // Identify users to add
            Set<UUID> currentStaffIds = currentStaff.stream().map(UserEntity::getId).collect(Collectors.toSet());
            List<UserEntity> toAdd = userRepository.findAllById(newStaffIds).stream()
                    .filter(user -> !currentStaffIds.contains(user.getId())
                            && user.getBusiness().getId().equals(businessId))
                    .collect(Collectors.toList());

            if (!toRemove.isEmpty() || !toAdd.isEmpty()) {
                changes.add(FieldChange.builder()
                        .field("Staff Access")
                        .before(currentStaff.size() + " assigned")
                        .after(newStaffIds.size() + " assigned")
                        .build());

                // Apply changes
                for (UserEntity u : toRemove) {
                    u.getBranches().remove(branch);
                    branch.getStaff().remove(u); // Sync in memory
                }
                userRepository.saveAll(toRemove);

                for (UserEntity u : toAdd) {
                    u.getBranches().add(branch);
                    branch.getStaff().add(u); // Sync in memory
                }
                userRepository.saveAll(toAdd);
            }
        }

        if (changes.isEmpty()) {
            log.info("No changes detected for branch {}", branchId);
            return mapToResponse(branch);
        }

        BranchEntity saved = branchRepository.save(branch);

        // Serialize changes to JSON and log
        String details = FieldChange.toJson(changes);
        activityLogService.logBranchUpdated(actor, branch.getBusiness(), saved, details);

        log.info("Branch {} updated. Changes: {}", branchId, changes.size());
        cacheService.evictAll(CacheName.BRANCHES, businessId);
        cacheService.evict(CacheName.BRANCH, businessId + CacheName.SEPARATOR + branchId + CacheName.SUFFIX_DETAILS);
        cacheService.evictAll(CacheName.USER_BRANCHES, businessId);
        cacheService.evictAll(CacheName.SERVICES, businessId);
        cacheService.evictAll(CacheName.BUSINESS_STAFF, businessId);
        cacheService.evictAll(CacheName.STAFF, businessId);
        return mapToResponse(saved);
    }

    @Transactional
    public void deleteBranch(UUID userId, UUID branchId, UUID businessId) {
        log.info("Deleting branch {} for business {}", branchId, businessId);
        UserEntity actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        BranchEntity branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));

        if (!branch.getBusiness().getId().equals(businessId)) {
            throw new BusinessException("Branch does not belong to your business");
        }

        // Log before deletion
        activityLogService.logBranchDeleted(actor, branch.getBusiness(), branch.getName());

        branchRepository.delete(branch);
        cacheService.evictAll(CacheName.BRANCHES, businessId);
        cacheService.evict(CacheName.BRANCH, businessId + CacheName.SEPARATOR + branchId + CacheName.SUFFIX_DETAILS);
        cacheService.evictAll(CacheName.USER_BRANCHES, businessId);
        cacheService.evictAll(CacheName.SERVICES, businessId);
        cacheService.evictAll(CacheName.BUSINESS_STAFF, businessId);
        cacheService.evictAll(CacheName.STAFF, businessId);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheName.BRANCH, keyGenerator = "businessKeyGenerator")
    public BranchResponse getBranchById(UUID branchId, @CacheBusinessId UUID businessId) {
        BranchEntity branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));

        if (!branch.getBusiness().getId().equals(businessId)) {
            throw new ResourceNotFoundException("Branch not found in your business");
        }

        return mapToResponse(branch);
    }

    private BranchResponse mapToResponse(BranchEntity branch) {
        Set<UserEntity> staffUsers = branch.getStaff() != null
                ? branch.getStaff().stream()
                        .filter(u -> u.getRole() == UserRole.STAFF)
                        .collect(Collectors.toSet())
                : Collections.emptySet();

        return BranchResponse.builder()
                .id(branch.getId())
                .businessId(branch.getBusiness().getId())
                .name(branch.getName())
                .address(branch.getAddress())
                .phoneNumber(branch.getPhoneNumber())
                .createdAt(branch.getCreatedAt())
                .updatedAt(branch.getUpdatedAt())
                .staffCount(staffUsers.size())
                .staffIds(staffUsers.stream().map(UserEntity::getId).collect(Collectors.toSet()))
                .build();
    }
}
