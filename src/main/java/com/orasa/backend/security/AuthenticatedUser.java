package com.orasa.backend.security;

import java.util.UUID;

public record AuthenticatedUser(
        UUID userId,
        UUID businessId,
        String role
) {}