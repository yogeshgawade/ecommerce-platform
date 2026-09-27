package com.ecommerce.gateway.fallback;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping("/auth")
    public Mono<ResponseEntity<Map<String, Object>>> authFallback() {
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(createErrorResponse("Auth Service", "Authentication service is currently unavailable")));
    }

    @RequestMapping("/catalog")
    public Mono<ResponseEntity<Map<String, Object>>> catalogFallback() {
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(createErrorResponse("Catalog Service", "Product catalog is currently unavailable")));
    }

    @RequestMapping("/cart")
    public Mono<ResponseEntity<Map<String, Object>>> cartFallback() {
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(createErrorResponse("Cart Service", "Cart service is currently unavailable")));
    }

    private Map<String, Object> createErrorResponse(String service, String message) {
        return Map.of(
                "timestamp", Instant.now().toString(),
                "status", HttpStatus.SERVICE_UNAVAILABLE.value(),
                "error", "SERVICE_UNAVAILABLE",
                "message", message,
                "service", service
        );
    }
}
