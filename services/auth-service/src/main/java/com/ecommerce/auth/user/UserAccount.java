package com.ecommerce.auth.user;

import java.time.Instant;
import java.util.UUID;

public record UserAccount(
        UUID id,
        String email,
        String passwordHash,
        UserRole role,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
