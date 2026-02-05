package com.orasa.backend.service;

import com.orasa.backend.common.UserRole;
import com.orasa.backend.domain.User;
import com.orasa.backend.dto.auth.AuthResponse;
import com.orasa.backend.dto.profile.UpdateProfileRequest;
import com.orasa.backend.exception.BusinessException;
import com.orasa.backend.exception.ResourceNotFoundException;
import com.orasa.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.orasa.backend.dto.activity.FieldChange;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;

    @Transactional
    public AuthResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String beforeUsername = user.getUsername();
        String beforeEmail = user.getEmail();
        
        updateUsername(user, request.username());
        updateEmail(user, request.email());

        User savedUser = userRepository.save(user);

        // Build details
        List<FieldChange> changes = new ArrayList<>();
        if (!beforeUsername.equals(savedUser.getUsername())) {
             changes.add(new FieldChange("Username", beforeUsername, savedUser.getUsername()));
        }
        if (beforeEmail != null && !beforeEmail.equals(savedUser.getEmail())) {
             changes.add(new FieldChange("Email", beforeEmail, savedUser.getEmail()));
        }

        if (!changes.isEmpty()) {
            activityLogService.logProfileUpdated(savedUser, savedUser.getBusiness(), FieldChange.toJson(changes));
        }

        return AuthResponse.builder()
                .userId(savedUser.getId())
                .role(savedUser.getRole())
                .businessId(savedUser.getBusiness() != null ? savedUser.getBusiness().getId() : null)
                .branchIds(savedUser.getBranches().stream().map(b -> b.getId()).toList())
                .build();
    }

    private void updateUsername(User user, String username) {
        if (username == null || username.isBlank()) return;
        if (username.equals(user.getUsername())) return;

        if (userRepository.existsByUsername(username)) {
            throw new BusinessException("Username already taken");
        }

        user.setUsername(username);
    }

    private void updateEmail(User user, String email) {
        if (email == null || email.isBlank()) return;
        if (user.getRole() != UserRole.OWNER) return;
        if (email.equals(user.getEmail())) return;

        user.setEmail(email);
    }
}
