package com.ecommerce.gateway.fallback;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FallbackControllerTest {

    private final FallbackController fallbackController = new FallbackController();

    @Test
    void authFallbackShouldReturn503() {
        var result = fallbackController.authFallback();

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(503, response.getStatusCode().value());
                    Map<String, Object> body = response.getBody();
                    assertNotNull(body);
                    assertEquals("SERVICE_UNAVAILABLE", body.get("error"));
                })
                .verifyComplete();
    }
}
