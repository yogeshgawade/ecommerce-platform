package com.ecommerce.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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

@WebFluxTest(controllers = CatalogAuthorizationProbeController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
@TestPropertySource(properties = "app.jwt.secret=test-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm")
class CatalogAuthorizationTest {

    private static final String TEST_SECRET =
            "test-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm";

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void anonymousCanReadProducts() {
        webTestClient.get().uri("/api/products")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("products");
    }

    @Test
    void anonymousCannotWriteProducts() {
        webTestClient.post().uri("/api/products")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void customerCannotWriteProducts() {
        webTestClient.post().uri("/api/products")
                .header("Authorization", bearer("CUSTOMER"))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCanWriteProducts() {
        webTestClient.post().uri("/api/products")
                .header("Authorization", bearer("ADMIN"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("created");
    }

    @Test
    void anonymousCannotAccessOrders() {
        webTestClient.get().uri("/api/orders")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void customerCanAccessOrders() {
        webTestClient.get().uri("/api/orders")
                .header("Authorization", bearer("CUSTOMER"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("orders");
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
