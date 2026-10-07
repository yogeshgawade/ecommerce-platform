package com.ecommerce.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import com.ecommerce.gateway.config.GatewayConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@WebFluxTest(controllers = PaymentAuthorizationProbeController.class)
@Import({GatewayConfig.class, SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
@TestPropertySource(properties = "app.jwt.secret=test-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm")
class PaymentAuthorizationTest {

    private static final String TEST_SECRET =
            "test-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm";

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void anonymousCannotReadPaymentStatus() {
        webTestClient.get().uri("/api/payments/orders/order-1")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void customerCanReadPaymentStatus() {
        webTestClient.get().uri("/api/payments/orders/order-1")
                .header("Authorization", bearer("CUSTOMER"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("payment");
    }

    @Test
    void stripeWebhookCanReachGatewayWithoutBearerToken() {
        webTestClient.post().uri("/api/payments/webhooks/stripe")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("webhook");
    }

    private String bearer(String role) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder()
                .subject("user-1")
                .claim("roles", new String[]{role})
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
    }
}
