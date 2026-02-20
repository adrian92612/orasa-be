package com.orasa.backend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orasa.backend.common.UserRole;
import com.orasa.backend.domain.UserEntity;
import com.orasa.backend.dto.activity.FieldChange;
import com.orasa.backend.dto.auth.AuthResponse;
import com.orasa.backend.dto.profile.UpdateProfileRequest;
import com.orasa.backend.exception.BusinessException;
import com.orasa.backend.exception.ResourceNotFoundException;
import com.orasa.backend.repository.UserRepository;
import com.orasa.backend.security.AuthenticatedUser;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;

    // In-memory cache for AuthenticatedUser to avoid DB hit on every request.
    // Records are final and cannot be serialized to Redis with NON_FINAL typing,
    // so we use a simple local cache instead.
    private final Map<UUID, AuthenticatedUser> authUserCache = new ConcurrentHashMap<>();

    @Transactional
    @CacheEvict(value = "currentUser", key = "#userId")
    public AuthResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String beforeUsername = user.getUsername();
        String beforeEmail = user.getEmail();
        
        updateUsername(user, request.username());
        updateEmail(user, request.email());

        UserEntity savedUser = userRepository.save(user);

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

        // Evict from local auth cache
        authUserCache.remove(userId);

        return AuthResponse.builder()
                .userId(savedUser.getId())
                .role(savedUser.getRole())
                .businessId(savedUser.getBusiness() != null ? savedUser.getBusiness().getId() : null)
                .build();
    }

    public AuthenticatedUser loadAuthenticatedUser(UUID userId) {
        return authUserCache.computeIfAbsent(userId, id ->
            userRepository.findById(id)
                .map(user -> new AuthenticatedUser(
                    user.getId(),
                    user.getBusiness() != null ? user.getBusiness().getId() : null,
                    user.getRole()
                ))
                .orElse(null)
        );
    }

    public void evictAuthenticatedUser(UUID userId) {
        authUserCache.remove(userId);
    }

    private void updateUsername(UserEntity user, String username) {
        if (username == null || username.isBlank()) return;
        if (username.equals(user.getUsername())) return;

        if (userRepository.existsByUsername(username)) {
            throw new BusinessException("Username already taken");
        }

        user.setUsername(username);
    }

    private void updateEmail(UserEntity user, String email) {
        if (email == null || email.isBlank()) return;
        if (user.getRole() != UserRole.OWNER) return;
        if (email.equals(user.getEmail())) return;

        user.setEmail(email);
    }
}
