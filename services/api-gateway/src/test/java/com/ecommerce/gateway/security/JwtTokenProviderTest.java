package com.ecommerce.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private final String testSecret = "test-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm";

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        try {
            var field = JwtTokenProvider.class.getDeclaredField("jwtSecret");
            field.setAccessible(true);
            field.set(jwtTokenProvider, testSecret);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void validateTokenShouldReturnClaimsForValidToken() {
        SecretKey key = Keys.hmacShaKeyFor(testSecret.getBytes(StandardCharsets.UTF_8));
        
        String token = Jwts.builder()
                .subject("user-123")
                .claim("email", "test@example.com")
                .claim("roles", new String[]{"CUSTOMER"})
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();

        Map<String, Object> claims = jwtTokenProvider.validateToken(token);

        assertEquals("user-123", claims.get("sub"));
        assertEquals("test@example.com", claims.get("email"));
    }

    @Test
    void validateTokenShouldThrowExceptionForInvalidToken() {
        assertThrows(RuntimeException.class, () -> {
            jwtTokenProvider.validateToken("invalid.token");
        });
    }
}
