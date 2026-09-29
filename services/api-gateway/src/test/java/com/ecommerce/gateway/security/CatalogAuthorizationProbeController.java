package com.ecommerce.gateway.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
class CatalogAuthorizationProbeController {

    @GetMapping("/api/products")
    Mono<String> get() {
        return Mono.just("products");
    }

    @PostMapping("/api/products")
    Mono<String> post() {
        return Mono.just("created");
    }

    @RequestMapping(path = "/api/orders")
    Mono<String> orders() {
        return Mono.just("orders");
    }
}
