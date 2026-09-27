package com.ecommerce.auth.auth;

import com.ecommerce.auth.user.UserAccount;
import com.ecommerce.auth.user.UserRole;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    @Test
    void createAccessTokenShouldReturnSignedJwt() {
        JwtService jwtService = new JwtService(
                "local-development-secret-change-this-to-a-long-random-value",
                900
        );

        UserAccount user = new UserAccount(
                UUID.randomUUID(),
                "yogesh@example.com",
                "hashed-password",
                UserRole.CUSTOMER,
                true,
                Instant.now(),
                Instant.now()
        );

        String token = jwtService.createAccessToken(user);

        assertNotNull(token);
        assertTrue(token.split("\\.").length == 3);
    }
}
