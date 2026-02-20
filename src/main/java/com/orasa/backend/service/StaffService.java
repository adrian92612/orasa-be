package com.orasa.backend.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orasa.backend.common.UserRole;
import com.orasa.backend.domain.BranchEntity;
import com.orasa.backend.domain.BusinessEntity;
import com.orasa.backend.domain.UserEntity;
import com.orasa.backend.dto.activity.FieldChange;
import com.orasa.backend.dto.staff.CreateStaffRequest;
import com.orasa.backend.dto.staff.StaffResponse;
import com.orasa.backend.dto.staff.UpdateStaffRequest;
import com.orasa.backend.exception.BusinessException;
import com.orasa.backend.exception.ResourceNotFoundException;
import com.orasa.backend.repository.BranchRepository;
import com.orasa.backend.repository.BusinessRepository;
import com.orasa.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StaffService {

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActivityLogService activityLogService;
    private final UserService userService;

    @Transactional
    @CacheEvict(value = "business-staff", key = "#businessId")
    public StaffResponse createStaff(UUID actorUserId, UUID businessId, CreateStaffRequest request) {
        UserEntity actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        BusinessEntity business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("Username already exists");
        }

        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email already exists");
        }

        Set<BranchEntity> branches = new HashSet<>();
        for (UUID branchId : request.getBranchIds()) {
            BranchEntity branch = branchRepository.findById(branchId)
                    .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + branchId));

            if (!branch.getBusiness().getId().equals(businessId)) {
                throw new BusinessException("Branch " + branchId + " does not belong to your business");
            }
            branches.add(branch);
        }

        UserEntity staff = UserEntity.builder()
                .business(business)
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getTemporaryPassword()))
                .role(UserRole.STAFF)

                .branches(branches)
                .build();

        UserEntity saved = userRepository.save(staff);
        
        // Log staff creation
        activityLogService.logStaffCreated(actor, business, saved.getUsername());
        
        return mapToResponse(saved);
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "currentUser", key = "#staffId"),
        @CacheEvict(value = "staff", key = "#staffId"),
        @CacheEvict(value = "business-staff", key = "#businessId")
    })
    public StaffResponse updateStaff(UUID actorUserId, UUID staffId, UUID businessId, UpdateStaffRequest request) {
        UserEntity actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        UserEntity staff = getStaffById(staffId, businessId);
        
        // Track changes
        List<FieldChange> changes = new ArrayList<>();
        String beforeEmail = staff.getEmail();
        Set<String> beforeBranches = staff.getBranches().stream()
                .map(BranchEntity::getName)
                .collect(Collectors.toSet());

        if (request.getEmail() != null) {
            if (!request.getEmail().equals(staff.getEmail()) && userRepository.existsByEmail(request.getEmail())) {
                throw new BusinessException("Email already exists");
            }
            if (!request.getEmail().equals(beforeEmail)) {
                changes.add(FieldChange.builder()
                        .field("Email")
                        .before(beforeEmail != null ? beforeEmail : "(not set)")
                        .after(request.getEmail())
                        .build());
            }
            staff.setEmail(request.getEmail());
        }

        if (request.getNewPassword() != null) {
            staff.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));

            changes.add(FieldChange.builder()
                    .field("Password")
                    .before("(hidden)")
                    .after("Reset")
                    .build());
        }

        if (request.getBranchIds() != null) {
            Set<BranchEntity> branches = new HashSet<>();
            for (UUID branchId : request.getBranchIds()) {
                BranchEntity branch = branchRepository.findById(branchId)
                        .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + branchId));

                if (!branch.getBusiness().getId().equals(businessId)) {
                    throw new BusinessException("Branch " + branchId + " does not belong to your business");
                }
                branches.add(branch);
            }
            
            Set<String> afterBranches = branches.stream()
                    .map(BranchEntity::getName)
                    .collect(Collectors.toSet());
            
            if (!beforeBranches.equals(afterBranches)) {
                changes.add(FieldChange.builder()
                        .field("Assigned Branches")
                        .before(String.join(", ", beforeBranches))
                        .after(String.join(", ", afterBranches))
                        .build());
            }
            staff.setBranches(branches);
        }

        UserEntity saved = userRepository.save(staff);
        
        // Evict from local auth cache
        userService.evictAuthenticatedUser(staffId);
        
        if (!changes.isEmpty()) {
            String details = FieldChange.toJson(changes);
            activityLogService.logStaffUpdated(actor, staff.getBusiness(), staff.getUsername(), details);
        }
        
        return mapToResponse(saved);
    }

    @Cacheable(value = "business-staff", key = "#businessId")
    public List<StaffResponse> getStaffByBusiness(UUID businessId) {
        return userRepository.findByBusinessId(businessId).stream()
                .filter(user -> user.getRole() == UserRole.STAFF)
                .map(this::mapToResponse)
                .toList();
    }

    @Cacheable(value = "staff", key = "#staffId")
    public StaffResponse getStaffMember(UUID staffId, UUID businessId) {
        UserEntity staff = getStaffById(staffId, businessId);
        return mapToResponse(staff);
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "currentUser", key = "#staffId"),
        @CacheEvict(value = "staff", key = "#staffId"),
        @CacheEvict(value = "business-staff", key = "#businessId")
    })
    public void deleteStaff(UUID actorUserId, UUID staffId, UUID businessId) {
        UserEntity actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        UserEntity staff = getStaffById(staffId, businessId);
        
        // Log before deletion
        activityLogService.logStaffDeactivated(actor, staff.getBusiness(), staff.getUsername());
        
        // Evict from local auth cache
        userService.evictAuthenticatedUser(staffId);
        
        userRepository.delete(staff);
    }


    private UserEntity getStaffById(UUID staffId, UUID businessId) {
        UserEntity staff = userRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found"));

        if (staff.getRole() != UserRole.STAFF) {
            throw new BusinessException("User is not a staff member");
        }

        if (staff.getBusiness() == null || !staff.getBusiness().getId().equals(businessId)) {
            throw new BusinessException("Staff member does not belong to your business");
        }

        return staff;
    }

    private StaffResponse mapToResponse(UserEntity staff) {
        List<StaffResponse.BranchInfo> branchInfos = staff.getBranches().stream()
                .map(branch -> StaffResponse.BranchInfo.builder()
                        .id(branch.getId())
                        .name(branch.getName())
                        .build())
                .toList();

        return StaffResponse.builder()
                .id(staff.getId())
                .businessId(staff.getBusiness().getId())
                .username(staff.getUsername())
                .email(staff.getEmail())
                .role(staff.getRole())

                .branches(branchInfos)
                .createdAt(staff.getCreatedAt())
                .updatedAt(staff.getUpdatedAt())
                .build();
    }
}

