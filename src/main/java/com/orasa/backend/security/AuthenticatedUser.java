package com.orasa.backend.security;

import java.util.UUID;

import com.orasa.backend.common.UserRole;

public record AuthenticatedUser(
        UUID userId,
        UUID businessId,
        UserRole role
) {}