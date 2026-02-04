package com.orasa.backend.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orasa.backend.common.UserRole;
import com.orasa.backend.domain.Branch;
import com.orasa.backend.domain.Business;
import com.orasa.backend.domain.User;
import com.orasa.backend.dto.staff.ChangePasswordRequest;
import com.orasa.backend.dto.staff.CreateStaffRequest;
import com.orasa.backend.dto.staff.StaffResponse;
import com.orasa.backend.dto.staff.UpdateStaffRequest;
import com.orasa.backend.exception.BusinessException;
import com.orasa.backend.exception.ResourceNotFoundException;
import com.orasa.backend.repository.BranchRepository;
import com.orasa.backend.repository.BusinessRepository;
import com.orasa.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StaffService {

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public StaffResponse createStaff(UUID businessId, CreateStaffRequest request) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("Username already exists");
        }

        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email already exists");
        }

        Set<Branch> branches = new HashSet<>();
        for (UUID branchId : request.getBranchIds()) {
            Branch branch = branchRepository.findById(branchId)
                    .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + branchId));

            if (!branch.getBusiness().getId().equals(businessId)) {
                throw new BusinessException("Branch " + branchId + " does not belong to your business");
            }
            branches.add(branch);
        }

        User staff = User.builder()
                .business(business)
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getTemporaryPassword()))
                .role(UserRole.STAFF)
                .mustChangePassword(true)
                .branches(branches)
                .build();

        User saved = userRepository.save(staff);
        return mapToResponse(saved);
    }

    @Transactional
    public StaffResponse updateStaff(UUID staffId, UUID businessId, UpdateStaffRequest request) {
        User staff = getStaffById(staffId, businessId);

        if (request.getEmail() != null) {
            if (!request.getEmail().equals(staff.getEmail()) && userRepository.existsByEmail(request.getEmail())) {
                throw new BusinessException("Email already exists");
            }
            staff.setEmail(request.getEmail());
        }

        if (request.getNewPassword() != null) {
            staff.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
            staff.setMustChangePassword(true);
        }

        if (request.getBranchIds() != null) {
            Set<Branch> branches = new HashSet<>();
            for (UUID branchId : request.getBranchIds()) {
                Branch branch = branchRepository.findById(branchId)
                        .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + branchId));

                if (!branch.getBusiness().getId().equals(businessId)) {
                    throw new BusinessException("Branch " + branchId + " does not belong to your business");
                }
                branches.add(branch);
            }
            staff.setBranches(branches);
        }

        User saved = userRepository.save(staff);
        return mapToResponse(saved);
    }

    public List<StaffResponse> getStaffByBusiness(UUID businessId) {
        return userRepository.findByBusinessId(businessId).stream()
                .filter(user -> user.getRole() == UserRole.STAFF)
                .map(this::mapToResponse)
                .toList();
    }

    public StaffResponse getStaffMember(UUID staffId, UUID businessId) {
        User staff = getStaffById(staffId, businessId);
        return mapToResponse(staff);
    }

    @Transactional
    public void deleteStaff(UUID staffId, UUID businessId) {
        User staff = getStaffById(staffId, businessId);
        userRepository.delete(staff);
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BusinessException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
    }

    private User getStaffById(UUID staffId, UUID businessId) {
        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found"));

        if (staff.getRole() != UserRole.STAFF) {
            throw new BusinessException("User is not a staff member");
        }

        if (staff.getBusiness() == null || !staff.getBusiness().getId().equals(businessId)) {
            throw new BusinessException("Staff member does not belong to your business");
        }

        return staff;
    }

    private StaffResponse mapToResponse(User staff) {
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
                .mustChangePassword(staff.isMustChangePassword())
                .branches(branchInfos)
                .createdAt(staff.getCreatedAt())
                .updatedAt(staff.getUpdatedAt())
                .build();
    }
}
