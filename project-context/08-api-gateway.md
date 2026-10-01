# 08-api-gateway


---

## File: `services/api-gateway/build.gradle`

```groovy
plugins {
	id 'java'
	id 'org.springframework.boot' version '4.1.1'
	id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.ecommerce'
version = '0.0.1-SNAPSHOT'

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation 'org.springframework.cloud:spring-cloud-starter-gateway-server-webflux'
	implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-reactor-resilience4j'
	implementation 'org.springframework.boot:spring-boot-starter-actuator'
	implementation 'org.springframework.boot:spring-boot-starter-security'
	implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'
	implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
	runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
	runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'
	testImplementation 'org.springframework.boot:spring-boot-starter-test'
	testImplementation 'org.springframework.boot:spring-boot-starter-webflux-test'
	testImplementation 'org.springframework.security:spring-security-test'
	testImplementation 'io.projectreactor:reactor-test'
	testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

dependencyManagement {
	imports {
		mavenBom 'org.springframework.cloud:spring-cloud-dependencies:2025.1.3'
	}
}

tasks.named('test') {
	useJUnitPlatform()
}

```

---

## File: `services/api-gateway/gradle/wrapper/gradle-wrapper.properties`

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-9.7.1-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists

```

---

## File: `services/api-gateway/HELP.md`

```markdown
# Getting Started

### Reference Documentation
For further reference, please consider the following sections:

* [Official Gradle documentation](https://docs.gradle.org)
* [Spring Boot Gradle Plugin Reference Guide](https://docs.spring.io/spring-boot/4.1.1/gradle-plugin)
* [Spring Cloud Gateway Reference](https://docs.spring.io/spring-cloud-gateway/reference/)

### Guides
The following guides illustrate how to use some features concretely:

* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)

### Additional Links
* [Gradle Build Scans](https://scans.gradle.com#gradle)

```

---

## File: `services/api-gateway/README.md`

```markdown
# API Gateway

Spring Cloud Gateway is the client-facing entry point. It validates JWT access tokens, applies role checks and Redis-backed request limits, and routes requests to internal services.

## Routes and access

| Path | Service | Access |
|---|---|---|
| `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout` | Auth Service | Public; Auth Service validates the submitted credentials or refresh token |
| `GET /auth/me` | Auth Service | Valid bearer token |
| `GET /api/products` and `/api/products/**` | Catalog Service | Public |
| `GET /search` | Search Service | Public |
| `POST /api/products` | Catalog Service | `ADMIN` |
| `PUT` or `DELETE /api/products/**` | Catalog Service | `ADMIN` |
| `/api/carts/**` | Cart Service | Valid bearer token |
| `/api/wishlist` and `/api/wishlist/**` | Wishlist Service | `CUSTOMER` or `ADMIN` bearer token; service also verifies JWT ownership |
| `GET /api/products/{id}/reviews` | Review Service | Public |
| `POST /api/products/{id}/reviews` | Review Service | `CUSTOMER` with a confirmed order containing the product |
| `PUT` or `DELETE /api/products/{id}/reviews/me` | Review Service | `CUSTOMER` |
| `/api/inventory/**` | Inventory Service | `ADMIN` |

The gateway and downstream services share `APP_JWT_SECRET` (wired from `JWT_SECRET` in Compose). The cart route also forwards the bearer token for service-level owner validation.

## Configuration

- `APP_CORS_ALLOWED_ORIGINS` is a comma-separated allowlist. The local defaults are `http://localhost:3000` and `http://localhost:3001`.
- Redis credentials and host settings configure the request limiter. Anonymous requests are keyed by peer IP; authenticated requests are keyed by the verified JWT subject. The resolver ignores caller-supplied identity headers.
- The default limiter allows 10 requests per second with a burst capacity of 20 per key.
- Only `/actuator/health` is exposed without authentication. Gateway route details are disabled on the public management endpoint.

## Run locally

Start Redis and the downstream services, then run:

```bash
./gradlew bootRun
```

Health check: `http://localhost:8080/actuator/health`.

```

---

## File: `services/api-gateway/settings.gradle`

```groovy
rootProject.name = 'api-gateway'

```

---

## File: `services/api-gateway/src/main/java/com/ecommerce/gateway/ApiGatewayApplication.java`

```java
package com.ecommerce.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApiGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiGatewayApplication.class, args);
	}
}

```

---

## File: `services/api-gateway/src/main/java/com/ecommerce/gateway/config/GatewayConfig.java`

```java
package com.ecommerce.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import reactor.core.publisher.Mono;

@Configuration
public class GatewayConfig {

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .filter(principal -> principal.getName() != null
                        && !principal.getName().isBlank()
                        && !"anonymousUser".equals(principal.getName()))
                .map(principal -> "user:" + principal.getName())
                .switchIfEmpty(Mono.fromSupplier(() -> "ip:" + clientAddress(exchange)));
    }

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:3001}") String allowedOrigins
    ) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        cors.setAllowedMethods(List.of(
                HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(), HttpMethod.DELETE.name(), HttpMethod.OPTIONS.name()
        ));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        cors.setAllowCredentials(false);
        cors.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    private String clientAddress(org.springframework.web.server.ServerWebExchange exchange) {
        var remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return "unknown";
        }
        return remoteAddress.getAddress().getHostAddress();
    }
}

```

---

## File: `services/api-gateway/src/main/java/com/ecommerce/gateway/fallback/FallbackController.java`

```java
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

    @RequestMapping("/order")
    public Mono<ResponseEntity<Map<String, Object>>> orderFallback() {
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(createErrorResponse("Order Service", "Order service is currently unavailable")));
    }

    @RequestMapping("/payment")
    public Mono<ResponseEntity<Map<String, Object>>> paymentFallback() {
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(createErrorResponse("Payment Service", "Payment service is currently unavailable")));
    }

    @RequestMapping("/search")
    public Mono<ResponseEntity<Map<String, Object>>> searchFallback() {
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(createErrorResponse("Search Service", "Product search is currently unavailable")));
    }

    @RequestMapping("/wishlist")
    public Mono<ResponseEntity<Map<String, Object>>> wishlistFallback() {
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(createErrorResponse("Wishlist Service", "Wishlist service is currently unavailable")));
    }

    @RequestMapping("/review")
    public Mono<ResponseEntity<Map<String, Object>>> reviewFallback() {
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(createErrorResponse("Review Service", "Product reviews are currently unavailable")));
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

```

---

## File: `services/api-gateway/src/main/java/com/ecommerce/gateway/security/JwtAuthenticationFilter.java`

```java
package com.ecommerce.gateway.security;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Collection;

@Component
public class JwtAuthenticationFilter implements WebFilter {

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (isPublicPath(path, exchange.getRequest().getMethod()) ||
            (HttpMethod.GET.equals(exchange.getRequest().getMethod())
                    && (path.equals("/api/products") || path.startsWith("/api/products/")))) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || authHeader.length() <= 7
                || !authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        try {
            Map<String, Object> claims = jwtTokenProvider.validateToken(token);
            
            Object subjectClaim = claims.get("sub");
            Object rolesClaim = claims.get("roles");
            if (!(subjectClaim instanceof String userId) || userId.isBlank()
                    || !(rolesClaim instanceof Collection<?> roles)
                    || roles.isEmpty()
                    || roles.stream().anyMatch(role -> !(role instanceof String))) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            List<SimpleGrantedAuthority> authorities = roles.stream()
                    .map(String.class::cast)
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();

            UsernamePasswordAuthenticationToken authentication = 
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);

            return chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));

        } catch (Exception e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    private boolean isPublicPath(String path, HttpMethod method) {
        return (HttpMethod.POST.equals(method)
                    && (path.equals("/auth/register") || path.equals("/auth/login")
                    || path.equals("/auth/refresh") || path.equals("/auth/logout")))
                || path.equals("/actuator/health")
                || (HttpMethod.POST.equals(method) && path.equals("/api/payments/webhooks/stripe"))
                || path.startsWith("/fallback/");
    }
}

```

---

## File: `services/api-gateway/src/main/java/com/ecommerce/gateway/security/JwtTokenProvider.java`

```java
package com.ecommerce.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class JwtTokenProvider {

    @Value("${app.jwt.secret:local-development-secret-change-this-to-a-long-random-value}")
    private String jwtSecret;

    public Map<String, Object> validateToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return claims.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue
                    ));
        } catch (Exception e) {
            throw new RuntimeException("Invalid JWT token", e);
        }
    }
}

```

---

## File: `services/api-gateway/src/main/java/com/ecommerce/gateway/security/SecurityConfig.java`

```java
package com.ecommerce.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            UrlBasedCorsConfigurationSource corsConfigurationSource
    ) {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .authorizeExchange(exchanges -> exchanges
                    .pathMatchers(
                            "/auth/register", "/auth/login", "/auth/refresh", "/auth/logout"
                    ).permitAll()
                    .pathMatchers("/auth/me").authenticated()
                    .pathMatchers("/actuator/health").permitAll()
                    .pathMatchers("/fallback/**").permitAll()
                    .pathMatchers(HttpMethod.GET, "/search", "/search/**").permitAll()
                    .pathMatchers("/api/wishlist", "/api/wishlist/**").hasAnyRole("CUSTOMER", "ADMIN")
                    .pathMatchers(HttpMethod.GET, "/api/products/*/reviews", "/api/products/*/reviews/**")
                        .permitAll()
                    .pathMatchers(HttpMethod.POST, "/api/products/*/reviews")
                        .hasRole("CUSTOMER")
                    .pathMatchers(HttpMethod.PUT, "/api/products/*/reviews/me")
                        .hasRole("CUSTOMER")
                    .pathMatchers(HttpMethod.DELETE, "/api/products/*/reviews/me")
                        .hasRole("CUSTOMER")
                    .pathMatchers("/api/carts/**").authenticated()
                    .pathMatchers("/api/orders", "/api/orders/**").hasAnyRole("CUSTOMER", "ADMIN")
                    .pathMatchers(HttpMethod.POST, "/api/payments/webhooks/stripe").permitAll()
                    .pathMatchers("/api/payments/**").hasAnyRole("CUSTOMER", "ADMIN")
                    .pathMatchers(HttpMethod.GET, "/api/products", "/api/products/**").permitAll()
                    .pathMatchers(HttpMethod.GET, "/api/inventory", "/api/inventory/**")
                    .hasAnyRole("CUSTOMER", "ADMIN")
                    .pathMatchers(HttpMethod.POST, "/api/products").hasRole("ADMIN")
                    .pathMatchers(HttpMethod.PUT, "/api/products/**").hasRole("ADMIN")
                    .pathMatchers(HttpMethod.DELETE, "/api/products/**").hasRole("ADMIN")
                    .pathMatchers("/api/inventory/**").hasRole("ADMIN")
                    .anyExchange().denyAll())
                .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
}

```

---

## File: `services/api-gateway/src/main/resources/application-docker.yml`

```yaml
spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      server:
        webflux:
          default-filters:
            - DedupeResponseHeader=Access-Control-Allow-Credentials Access-Control-Allow-Origin
            - RemoveResponseHeader=Server
            - name: RequestRateLimiter
              args:
                key-resolver: "#{@userKeyResolver}"
                redis-rate-limiter.replenishRate: 10
                redis-rate-limiter.burstCapacity: 20
                redis-rate-limiter.requestedTokens: 1
          routes:
            - id: review-service
              uri: http://review-service:8000
              predicates:
                - Path=/api/products/*/reviews,/api/products/*/reviews/**
              filters:
                - name: CircuitBreaker
                  args:
                    name: reviewCircuitBreaker
                    fallbackUri: forward:/fallback/review
            - id: auth-service
              uri: http://auth-service:8082
              predicates:
                - Path=/auth/**
              filters:
                - name: CircuitBreaker
                  args:
                    name: authCircuitBreaker
                    fallbackUri: forward:/fallback/auth
            - id: catalog-service
              uri: http://catalog-service:8081
              predicates:
                - Path=/api/products,/api/products/**
              filters:
                - name: CircuitBreaker
                  args:
                    name: catalogCircuitBreaker
                    fallbackUri: forward:/fallback/catalog
            - id: cart-service
              uri: http://cart-service:8000
              predicates:
                - Path=/api/carts/**
              filters:
                - name: CircuitBreaker
                  args:
                    name: cartCircuitBreaker
                    fallbackUri: forward:/fallback/cart
            - id: inventory-service
              uri: http://inventory-service:8083
              predicates:
                - Path=/api/inventory/**
            - id: order-service
              uri: http://order-service:8084
              predicates:
                - Path=/api/orders,/api/orders/**
              filters:
                - name: CircuitBreaker
                  args:
                    name: orderCircuitBreaker
                    fallbackUri: forward:/fallback/order
            - id: payment-service
              uri: http://payment-service:8000
              predicates:
                - Path=/api/payments/**
              filters:
                - name: CircuitBreaker
                  args:
                    name: paymentCircuitBreaker
                    fallbackUri: forward:/fallback/payment
            - id: search-service
              uri: http://search-service:8000
              predicates:
                - Path=/search,/search/**
              filters:
                - name: CircuitBreaker
                  args:
                    name: searchCircuitBreaker
                    fallbackUri: forward:/fallback/search
            - id: wishlist-service
              uri: http://wishlist-service:8000
              predicates:
                - Path=/api/wishlist,/api/wishlist/**
              filters:
                - name: CircuitBreaker
                  args:
                    name: wishlistCircuitBreaker
                    fallbackUri: forward:/fallback/wishlist
            - id: fallback-route
              uri: no://op
              predicates:
                - Path=/fallback/**
              filters:
                - StripPrefix=1
  data:
    redis:
      host: ${REDIS_HOST:redis}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:redis_dev_password}

server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: never
    gateway:
      enabled: false

app:
  jwt:
    secret: ${APP_JWT_SECRET:local-development-secret-change-this-to-a-long-random-value}
  cors:
    allowed-origins: ${APP_CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:3001}

```

---

## File: `services/api-gateway/src/main/resources/application.yml`

```yaml
spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      server:
        webflux:
          default-filters:
            - DedupeResponseHeader=Access-Control-Allow-Credentials Access-Control-Allow-Origin
            - RemoveResponseHeader=Server
            - name: RequestRateLimiter
              args:
                key-resolver: "#{@userKeyResolver}"
                redis-rate-limiter.replenishRate: 10
                redis-rate-limiter.burstCapacity: 20
                redis-rate-limiter.requestedTokens: 1
          routes:
            - id: review-service
              uri: http://localhost:8005
              predicates:
                - Path=/api/products/*/reviews,/api/products/*/reviews/**
              filters:
                - name: CircuitBreaker
                  args:
                    name: reviewCircuitBreaker
                    fallbackUri: forward:/fallback/review
            - id: auth-service
              uri: http://localhost:8082
              predicates:
                - Path=/auth/**
              filters:
                - name: CircuitBreaker
                  args:
                    name: authCircuitBreaker
                    fallbackUri: forward:/fallback/auth
            - id: catalog-service
              uri: http://localhost:8081
              predicates:
                - Path=/api/products,/api/products/**
              filters:
                - name: CircuitBreaker
                  args:
                    name: catalogCircuitBreaker
                    fallbackUri: forward:/fallback/catalog
            - id: cart-service
              uri: http://localhost:8001
              predicates:
                - Path=/api/carts/**
              filters:
                - name: CircuitBreaker
                  args:
                    name: cartCircuitBreaker
                    fallbackUri: forward:/fallback/cart
            - id: inventory-service
              uri: http://localhost:8083
              predicates:
                - Path=/api/inventory/**
            - id: order-service
              uri: http://localhost:8084
              predicates:
                - Path=/api/orders,/api/orders/**
              filters:
                - name: CircuitBreaker
                  args:
                    name: orderCircuitBreaker
                    fallbackUri: forward:/fallback/order
            - id: payment-service
              uri: http://localhost:8002
              predicates:
                - Path=/api/payments/**
              filters:
                - name: CircuitBreaker
                  args:
                    name: paymentCircuitBreaker
                    fallbackUri: forward:/fallback/payment
            - id: search-service
              uri: http://localhost:8003
              predicates:
                - Path=/search,/search/**
              filters:
                - name: CircuitBreaker
                  args:
                    name: searchCircuitBreaker
                    fallbackUri: forward:/fallback/search
            - id: wishlist-service
              uri: http://localhost:8004
              predicates:
                - Path=/api/wishlist,/api/wishlist/**
              filters:
                - name: CircuitBreaker
                  args:
                    name: wishlistCircuitBreaker
                    fallbackUri: forward:/fallback/wishlist
            - id: fallback-route
              uri: no://op
              predicates:
                - Path=/fallback/**
              filters:
                - StripPrefix=1
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:redis_dev_password}

server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: never
    gateway:
      enabled: false

app:
  jwt:
    secret: ${APP_JWT_SECRET:local-development-secret-change-this-to-a-long-random-value}
  cors:
    allowed-origins: ${APP_CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:3001}

```

---

## File: `services/api-gateway/src/test/java/com/ecommerce/gateway/ApiGatewayApplicationTests.java`

```java
package com.ecommerce.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ApiGatewayApplicationTests {

	@Test
	void contextLoads() {
	}
}

```

---

## File: `services/api-gateway/src/test/java/com/ecommerce/gateway/fallback/FallbackControllerTest.java`

```java
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

```

---

## File: `services/api-gateway/src/test/java/com/ecommerce/gateway/security/CatalogAuthorizationProbeController.java`

```java
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

```

---

## File: `services/api-gateway/src/test/java/com/ecommerce/gateway/security/CatalogAuthorizationTest.java`

```java
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

```

---

## File: `services/api-gateway/src/test/java/com/ecommerce/gateway/security/JwtTokenProviderTest.java`

```java
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

```

---

## File: `services/api-gateway/src/test/java/com/ecommerce/gateway/security/PaymentAuthorizationProbeController.java`

```java
package com.ecommerce.gateway.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PaymentAuthorizationProbeController {

    @GetMapping("/api/payments/orders/order-1")
    String paymentStatus() {
        return "payment";
    }

    @PostMapping("/api/payments/webhooks/stripe")
    String stripeWebhook() {
        return "webhook";
    }
}

```

---

## File: `services/api-gateway/src/test/java/com/ecommerce/gateway/security/PaymentAuthorizationTest.java`

```java
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

@WebFluxTest(controllers = PaymentAuthorizationProbeController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
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

```
