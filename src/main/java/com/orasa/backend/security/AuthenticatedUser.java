package com.orasa.backend.security;

import java.util.UUID;

public record AuthenticatedUser(
        UUID userId,
        String username,
        UUID businessId,
        String businessName,
        com.orasa.backend.common.UserRole role
) {}