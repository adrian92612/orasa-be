package com.orasa.backend.service;

import java.util.stream.Collectors;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orasa.backend.common.UserRole;
import com.orasa.backend.domain.BranchEntity;
import com.orasa.backend.domain.BusinessEntity;
import com.orasa.backend.domain.ServiceEntity;
import com.orasa.backend.domain.UserEntity;
import com.orasa.backend.domain.BranchServiceEntity;

import com.orasa.backend.dto.activity.FieldChange;
import com.orasa.backend.dto.branch.BranchResponse;
import com.orasa.backend.dto.branch.CreateBranchRequest;
import com.orasa.backend.dto.branch.UpdateBranchRequest;

import com.orasa.backend.exception.ResourceNotFoundException;
import com.orasa.backend.exception.BusinessException;
import com.orasa.backend.repository.BranchRepository;
import com.orasa.backend.repository.BranchServiceRepository;
import com.orasa.backend.repository.BusinessRepository;
import com.orasa.backend.repository.ServiceRepository;
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
    private final ServiceRepository serviceRepository;
    private final BranchServiceRepository branchServiceRepository;

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "branches", key = "#businessId"),
        @CacheEvict(value = "user-branches", allEntries = true)
    })
    public BranchResponse createBranch(UUID ownerId, UUID businessId, CreateBranchRequest request) {
        log.info("Creating new branch '{}' for business {}", request.getName(), businessId);
        UserEntity owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found"));
        
        BusinessEntity business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));

        BranchEntity branch = BranchEntity.builder()
                .business(business)
                .name(request.getName())
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

        // Handle Services
        java.util.Set<UUID> serviceIds = request.getServiceIds();
        if (serviceIds == null) {
            serviceIds = java.util.Collections.emptySet();
        }
        updateBranchServices(saved, serviceIds, new ArrayList<>());

        owner.getBranches().add(saved);
        userRepository.save(owner);

        // Log branch creation
        activityLogService.logBranchCreated(owner, business, saved);

        log.info("Branch created with ID: {}", saved.getId());
        return mapToResponse(saved);
    }

    @Cacheable(value = "branches", key = "#businessId")
    public List<BranchResponse> getBranchesByBusiness(UUID businessId) {
        List<BranchEntity> branches = branchRepository.findByBusinessId(businessId);
        return branches.stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Cacheable(value = "user-branches", key = "#userId")
    public List<BranchResponse> getBranchesForUser(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        return user.getBranches().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "branches", key = "#businessId"),
        @CacheEvict(value = "branch", key = "#branchId"),
        @CacheEvict(value = "user-branches", allEntries = true)
    })
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
        if (!branch.getName().equals(request.getName().trim())) {
            changes.add(FieldChange.builder()
                    .field("Name")
                    .before(branch.getName())
                    .after(request.getName().trim())
                    .build());
            branch.setName(request.getName().trim());
        }

        // Check Address change
        String oldAddress = branch.getAddress();
        String newAddress = request.getAddress();
        String addressToSet = (newAddress == null || newAddress.trim().isEmpty()) ? null : newAddress.trim();

        if (!java.util.Objects.equals(oldAddress, addressToSet)) {
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
        
        if (!java.util.Objects.equals(oldPhone, phoneToSet)) {
            changes.add(FieldChange.builder()
                    .field("Phone Number")
                    .before(oldPhone != null ? oldPhone : "")
                    .after(phoneToSet != null ? phoneToSet : "")
                    .build());
            branch.setPhoneNumber(phoneToSet);
        }

        // Check Staff Assignment changes
        if (request.getStaffIds() != null) {
            java.util.Set<UUID> newStaffIds = request.getStaffIds();
            java.util.Set<UserEntity> currentStaff = branch.getStaff();
            
            // Identify users to remove
            List<UserEntity> toRemove = currentStaff.stream()
                    .filter(staff -> !newStaffIds.contains(staff.getId()))
                    .collect(Collectors.toList());
            
            // Identify users to add
            java.util.Set<UUID> currentStaffIds = currentStaff.stream().map(UserEntity::getId).collect(Collectors.toSet());
            List<UserEntity> toAdd = userRepository.findAllById(newStaffIds).stream()
                    .filter(user -> !currentStaffIds.contains(user.getId()) && user.getBusiness().getId().equals(businessId))
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

        // Check Service changes
        if (request.getServiceIds() != null) {
            updateBranchServices(branch, request.getServiceIds(), changes);
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
        return mapToResponse(saved);
    }

    private void updateBranchServices(BranchEntity branch, java.util.Set<UUID> requestedActiveServiceIds, List<FieldChange> changes) {
        List<ServiceEntity> allServices = serviceRepository.findByBusinessId(branch.getBusiness().getId());
        List<BranchServiceEntity> existingOverrides = branchServiceRepository.findByBranchId(branch.getId());
        java.util.Map<UUID, BranchServiceEntity> overrideMap = existingOverrides.stream()
                .collect(Collectors.toMap(bs -> bs.getService().getId(), bs -> bs));

        List<BranchServiceEntity> toSave = new ArrayList<>();
        int oldActiveCount = 0; // Count previously active services
        int newActiveCount = 0; // Count effectively active services
        boolean needsSave = false;

        for (ServiceEntity service : allServices) {
            // Determine current state (before this update)
            BranchServiceEntity override = overrideMap.get(service.getId());
            boolean wasActive = (override != null) && override.isActive();

            if (wasActive) {
                oldActiveCount++;
            }

            // Determine target state (after this update)
            boolean shouldBeActive = requestedActiveServiceIds.contains(service.getId());
            if (shouldBeActive) {
                newActiveCount++;
            }

            boolean statusChanged = wasActive != shouldBeActive;
            boolean missingRecord = override == null;

            if (statusChanged || (shouldBeActive && missingRecord)) {
                needsSave = true;
                if (override == null) {
                    override = BranchServiceEntity.builder()
                            .branchId(branch.getId())
                            .service(service)
                            .isActive(shouldBeActive)
                            .build();
                } else {
                    override.setActive(shouldBeActive);
                }
                toSave.add(override);
            }
        }

        if (needsSave) {
            branchServiceRepository.saveAll(toSave);
            if (oldActiveCount != newActiveCount) {
                changes.add(FieldChange.builder()
                        .field("Services")
                        .before(oldActiveCount + " Active")
                        .after(newActiveCount + " Active")
                        .build());
            }
        }
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "branches", key = "#businessId"),
        @CacheEvict(value = "branch", key = "#branchId"),
        @CacheEvict(value = "user-branches", allEntries = true)
    })
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
    }

    @Cacheable(value = "branch", key = "#branchId")
    public BranchResponse getBranchById(UUID branchId) {
        BranchEntity branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));

        return mapToResponse(branch);
    }

    private BranchResponse mapToResponse(BranchEntity branch) {
        List<BranchServiceEntity> overrides = branchServiceRepository.findByBranchId(branch.getId());

        java.util.Set<UUID> activeServiceIds = overrides.stream()
                .filter(BranchServiceEntity::isActive)
                .map(bs -> bs.getService().getId())
                .collect(Collectors.toSet());

        java.util.Set<UserEntity> staffUsers = branch.getStaff() != null 
                ? branch.getStaff().stream()
                    .filter(u -> u.getRole() == UserRole.STAFF)
                    .collect(Collectors.toSet())
                : java.util.Collections.emptySet();

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
                .serviceCount(activeServiceIds.size())
                .activeServiceIds(activeServiceIds)
                .build();
    }
}
