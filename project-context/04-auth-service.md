# 04-auth-service


---

## File: `services/auth-service/build.gradle`

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
	implementation 'org.springframework.boot:spring-boot-starter-actuator'
	implementation 'org.springframework.boot:spring-boot-starter-jdbc'
	implementation 'org.springframework.boot:spring-boot-starter-security'
	implementation 'org.springframework.boot:spring-boot-starter-validation'
	implementation 'org.springframework.boot:spring-boot-starter-webmvc'
	implementation 'org.springframework.boot:spring-boot-starter-flyway'
	implementation 'org.flywaydb:flyway-database-postgresql'
	implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
	runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
	runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'
	runtimeOnly 'org.postgresql:postgresql'
	testImplementation 'org.springframework.boot:spring-boot-starter-actuator-test'
	testImplementation 'org.springframework.boot:spring-boot-starter-jdbc-test'
	testImplementation 'org.springframework.boot:spring-boot-starter-security-test'
	testImplementation 'org.springframework.boot:spring-boot-starter-validation-test'
	testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
	testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') {
	useJUnitPlatform()
}

```

---

## File: `services/auth-service/gradle/wrapper/gradle-wrapper.properties`

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-9.7.1-bin.zip
networkTimeout=10000
retries=0
retryBackOffMs=500
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists

```

---

## File: `services/auth-service/HELP.md`

```markdown
# Getting Started

### Reference Documentation
For further reference, please consider the following sections:

* [Official Gradle documentation](https://docs.gradle.org)
* [Spring Boot Gradle Plugin Reference Guide](https://docs.spring.io/spring-boot/4.1.1/gradle-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/4.1.1/gradle-plugin/packaging-oci-image.html)
* [Spring Web](https://docs.spring.io/spring-boot/4.1.1/reference/web/servlet.html)
* [JDBC API](https://docs.spring.io/spring-boot/4.1.1/reference/data/sql.html)
* [Validation](https://docs.spring.io/spring-boot/4.1.1/reference/io/validation.html)
* [Spring Boot Actuator](https://docs.spring.io/spring-boot/4.1.1/reference/actuator/index.html)
* [Spring Security](https://docs.spring.io/spring-boot/4.1.1/reference/web/spring-security.html)

### Guides
The following guides illustrate how to use some features concretely:

* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)
* [Accessing Relational Data using JDBC with Spring](https://spring.io/guides/gs/relational-data-access/)
* [Managing Transactions](https://spring.io/guides/gs/managing-transactions/)
* [Validation](https://spring.io/guides/gs/validating-form-input/)
* [Building a RESTful Web Service with Spring Boot Actuator](https://spring.io/guides/gs/actuator-service/)
* [Securing a Web Application](https://spring.io/guides/gs/securing-web/)
* [Spring Boot and OAuth2](https://spring.io/guides/tutorials/spring-boot-oauth2/)
* [Authenticating a User with LDAP](https://spring.io/guides/gs/authenticating-ldap/)

### Additional Links
These additional references should also help you:

* [Gradle Build Scans – insights for your project's build](https://scans.gradle.com#gradle)


```

---

## File: `services/auth-service/README.md`

```markdown
# Auth Service

Auth Service manages user accounts, issues HS256 access tokens, and stores only hashes of refresh-token secrets in PostgreSQL.

## Configuration

| Variable | Property | Purpose |
|---|---|---|
| `APP_JWT_SECRET` | `app.jwt.secret` | Shared signing key. Auth Service, API Gateway, and services validating access tokens must use the same value. |
| `APP_JWT_ACCESS_TOKEN_EXPIRATION_SECONDS` | `app.jwt.access-token-expiration-seconds` | Access-token lifetime; defaults to 900 seconds. |
| `APP_JWT_REFRESH_TOKEN_EXPIRATION_SECONDS` | `app.jwt.refresh-token-expiration-seconds` | Refresh-token lifetime; defaults to 604800 seconds. |
| `SPRING_DATASOURCE_URL` | `spring.datasource.url` | PostgreSQL connection URL. |
| `SPRING_DATASOURCE_USERNAME` | `spring.datasource.username` | PostgreSQL username. |
| `SPRING_DATASOURCE_PASSWORD` | `spring.datasource.password` | PostgreSQL password. |

Docker Compose supplies the JWT secret and database credentials from the project-level environment file. Local fallback values are for development only.

## Endpoints

- `POST /auth/register` creates a customer account. Roles cannot be selected during registration.
- `POST /auth/login` returns an access token and refresh token.
- `POST /auth/refresh` rotates a refresh token. A token can win only one concurrent rotation.
- `POST /auth/logout` revokes the supplied refresh token.
- `GET /auth/me` requires a valid bearer access token.

Access tokens carry `sub`, `email`, and `roles` claims. Protected Auth Service routes validate the signature, expiration, subject, and role claims locally, in addition to gateway validation.

```

---

## File: `services/auth-service/settings.gradle`

```groovy
rootProject.name = 'auth-service'

```

---

## File: `services/auth-service/src/main/java/com/ecommerce/auth/auth/AuthController.java`

```java
package com.ecommerce.auth.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDtos.UserResponse register(
            @Valid @RequestBody AuthDtos.RegisterRequest request
    ) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthDtos.TokenResponse login(
            @Valid @RequestBody AuthDtos.LoginRequest request
    ) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AuthDtos.TokenResponse refresh(
            @Valid @RequestBody AuthDtos.RefreshRequest request
    ) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @Valid @RequestBody AuthDtos.LogoutRequest request
    ) {
        authService.logout(request);
    }

    @GetMapping("/me")
    public AuthDtos.UserResponse me(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return authService.currentUser(userId);
    }
}

```

---

## File: `services/auth-service/src/main/java/com/ecommerce/auth/auth/AuthDtos.java`

```java
package com.ecommerce.auth.auth;

import com.ecommerce.auth.user.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 100) String password
    ) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {
    }

    public record RefreshRequest(
            @NotBlank String refreshToken
    ) {
    }

    public record LogoutRequest(
            @NotBlank String refreshToken
    ) {
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn
    ) {
    }

    public record UserResponse(
            UUID id,
            String email,
            UserRole role
    ) {
    }
}

```

---

## File: `services/auth-service/src/main/java/com/ecommerce/auth/auth/AuthService.java`

```java
package com.ecommerce.auth.auth;

import com.ecommerce.auth.token.RefreshToken;
import com.ecommerce.auth.token.RefreshTokenRepository;
import com.ecommerce.auth.user.UserAccount;
import com.ecommerce.auth.user.UserRepository;
import com.ecommerce.auth.user.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final long refreshExpirationSeconds;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            org.springframework.core.env.Environment environment
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshExpirationSeconds = Long.parseLong(
                environment.getProperty(
                        "app.jwt.refresh-token-expiration-seconds",
                        "604800"
                )
        );
        if (refreshExpirationSeconds <= 0) {
            throw new IllegalArgumentException("Refresh-token expiration must be positive");
        }
    }

    public AuthDtos.UserResponse register(AuthDtos.RegisterRequest request) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email is already registered"
            );
        }

        Instant now = Instant.now();

        UserAccount user = new UserAccount(
                UUID.randomUUID(),
                email,
                passwordEncoder.encode(request.password()),
                UserRole.CUSTOMER,
                true,
                now,
                now
        );

        try {
            userRepository.save(user);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email is already registered",
                    exception
            );
        }

        return toResponse(user);
    }

    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request) {
        UserAccount user = userRepository.findByEmail(
                        normalizeEmail(request.email())
                )
                .orElseThrow(this::invalidCredentials);

        if (!user.enabled()
                || !passwordEncoder.matches(
                request.password(),
                user.passwordHash()
        )) {
            throw invalidCredentials();
        }

        return issueTokens(user);
    }

    public AuthDtos.UserResponse currentUser(UUID userId) {
        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found"
                ));

        return toResponse(user);
    }

    @Transactional
    public AuthDtos.TokenResponse refresh(AuthDtos.RefreshRequest request) {
        UUID presentedTokenId = extractRefreshTokenId(request.refreshToken());
        String rawToken = extractRawRefreshToken(request.refreshToken());
        String hash = hashToken(rawToken);

        RefreshToken oldToken = refreshTokenRepository.findActiveByHash(hash)
                .orElseThrow(this::invalidRefreshToken);

        if (!oldToken.id().equals(presentedTokenId)
                || oldToken.expiresAt().isBefore(Instant.now())) {
            throw invalidRefreshToken();
        }

        UserAccount user = userRepository.findById(oldToken.userId())
                .orElseThrow(this::invalidRefreshToken);
        if (!user.enabled()) {
            throw invalidRefreshToken();
        }

        AuthDtos.TokenResponse response = issueTokens(user);
        UUID replacementId = UUID.fromString(
                response.refreshToken().substring(0, 36)
        );

        if (refreshTokenRepository.revoke(oldToken.id(), replacementId) != 1) {
            throw invalidRefreshToken();
        }

        return response;
    }

    public void logout(AuthDtos.LogoutRequest request) {
        refreshTokenRepository.revokeByHash(
                hashToken(extractRawRefreshToken(request.refreshToken()))
        );
    }

    private AuthDtos.TokenResponse issueTokens(UserAccount user) {
        String accessToken = jwtService.createAccessToken(user);
        String rawRefreshToken = createRefreshToken();
        UUID refreshId = UUID.randomUUID();

        RefreshToken refreshToken = new RefreshToken(
                refreshId,
                user.id(),
                hashToken(rawRefreshToken),
                Instant.now().plusSeconds(refreshExpirationSeconds),
                false,
                Instant.now(),
                null
        );

        refreshTokenRepository.save(refreshToken);

        // Prefix the raw token with its ID so rotation can identify replacement.
        String responseRefreshToken = refreshId + "." + rawRefreshToken;

        return new AuthDtos.TokenResponse(
                accessToken,
                responseRefreshToken,
                "Bearer",
                jwtService.expirationSeconds()
        );
    }

    private String createRefreshToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(token.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String extractRawRefreshToken(String token) {
        int separator = token.indexOf('.');
        if (separator <= 0 || separator == token.length() - 1) {
            throw invalidRefreshToken();
        }

        return token.substring(separator + 1);
    }

    private UUID extractRefreshTokenId(String token) {
        int separator = token.indexOf('.');
        if (separator <= 0 || separator == token.length() - 1) {
            throw invalidRefreshToken();
        }
        try {
            return UUID.fromString(token.substring(0, separator));
        } catch (IllegalArgumentException exception) {
            throw invalidRefreshToken();
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private AuthDtos.UserResponse toResponse(UserAccount user) {
        return new AuthDtos.UserResponse(
                user.id(),
                user.email(),
                user.role()
        );
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Invalid email or password"
        );
    }

    private ResponseStatusException invalidRefreshToken() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Invalid refresh token"
        );
    }
}

```

---

## File: `services/auth-service/src/main/java/com/ecommerce/auth/auth/JwtService.java`

```java
package com.ecommerce.auth.auth;

import com.ecommerce.auth.user.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationSeconds;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiration-seconds}") long expirationSeconds
    ) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException(
                    "JWT secret must contain at least 32 UTF-8 bytes"
            );
        }
        if (expirationSeconds <= 0) {
            throw new IllegalArgumentException("Access-token expiration must be positive");
        }

        this.secretKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
        this.expirationSeconds = expirationSeconds;
    }

    public String createAccessToken(UserAccount user) {
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(user.id().toString())
                .claim("email", user.email())
                .claim("roles", new String[]{user.role().name()})
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public long expirationSeconds() {
        return expirationSeconds;
    }

    public Claims parseAccessToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

```

---

## File: `services/auth-service/src/main/java/com/ecommerce/auth/AuthServiceApplication.java`

```java
package com.ecommerce.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AuthServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuthServiceApplication.class, args);
	}

}

```

---

## File: `services/auth-service/src/main/java/com/ecommerce/auth/config/JwtAuthenticationFilter.java`

```java
package com.ecommerce.auth.config;

import com.ecommerce.auth.auth.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!authorization.startsWith("Bearer ") || authorization.length() == 7) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid bearer token");
            return;
        }

        try {
            Claims claims = jwtService.parseAccessToken(authorization.substring(7));
            String subject = claims.getSubject();
            Object rolesClaim = claims.get("roles");
            if (subject == null || !(rolesClaim instanceof Collection<?> roles)
                    || roles.isEmpty() || roles.stream().anyMatch(role -> !(role instanceof String))) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid bearer token claims");
                return;
            }

            UUID.fromString(subject);
            List<SimpleGrantedAuthority> authorities = roles.stream()
                    .map(String.class::cast)
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(subject, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid bearer token");
            return;
        }

        filterChain.doFilter(request, response);
    }
}

```

---

## File: `services/auth-service/src/main/java/com/ecommerce/auth/config/SecurityConfig.java`

```java
package com.ecommerce.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

```

---

## File: `services/auth-service/src/main/java/com/ecommerce/auth/config/WebSecurityConfig.java`

```java
package com.ecommerce.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public WebSecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/auth/register",
                                "/auth/login",
                                "/auth/refresh",
                                "/auth/logout",
                                "/actuator/health",
                                "/actuator/info",
                                "/error"
                        )
                        .permitAll()
                        .requestMatchers("/auth/me").authenticated()
                        .anyRequest().denyAll()
                )
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterServletRegistration(
            JwtAuthenticationFilter filter
    ) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}

```

---

## File: `services/auth-service/src/main/java/com/ecommerce/auth/token/RefreshToken.java`

```java
package com.ecommerce.auth.token;

import java.time.Instant;
import java.util.UUID;

public record RefreshToken(
        UUID id,
        UUID userId,
        String tokenHash,
        Instant expiresAt,
        boolean revoked,
        Instant createdAt,
        UUID replacedBy
) {
}

```

---

## File: `services/auth-service/src/main/java/com/ecommerce/auth/token/RefreshTokenRepository.java`

```java
package com.ecommerce.auth.token;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RefreshTokenRepository {

    private final JdbcClient jdbcClient;

    public RefreshTokenRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public RefreshToken save(RefreshToken token) {
        jdbcClient.sql("""
                        INSERT INTO refresh_tokens (
                            id, user_id, token_hash, expires_at, revoked,
                            created_at, replaced_by
                        )
                        VALUES (
                            :id, :userId, :tokenHash, :expiresAt, :revoked,
                            :createdAt, :replacedBy
                        )
                        """)
                .param("id", token.id())
                .param("userId", token.userId())
                .param("tokenHash", token.tokenHash())
                .param("expiresAt", token.expiresAt().atOffset(ZoneOffset.UTC))
                .param("revoked", token.revoked())
                .param("createdAt", token.createdAt().atOffset(ZoneOffset.UTC))
                .param("replacedBy", token.replacedBy())
                .update();

        return token;
    }

    public Optional<RefreshToken> findActiveByHash(String tokenHash) {
        return jdbcClient.sql("""
                        SELECT id, user_id, token_hash, expires_at, revoked,
                               created_at, replaced_by
                        FROM refresh_tokens
                        WHERE token_hash = :tokenHash
                          AND revoked = FALSE
                          AND expires_at > CURRENT_TIMESTAMP
                        """)
                .param("tokenHash", tokenHash)
                .query((rs, rowNum) -> new RefreshToken(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("token_hash"),
                        rs.getTimestamp("expires_at").toInstant(),
                        rs.getBoolean("revoked"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getObject("replaced_by", UUID.class)
                ))
                .optional();
    }

    public int revoke(UUID id, UUID replacedBy) {
        return jdbcClient.sql("""
                        UPDATE refresh_tokens
                        SET revoked = TRUE, replaced_by = :replacedBy
                        WHERE id = :id
                          AND revoked = FALSE
                          AND expires_at > CURRENT_TIMESTAMP
                        """)
                .param("id", id)
                .param("replacedBy", replacedBy)
                .update();
    }

    public void revokeByHash(String tokenHash) {
        jdbcClient.sql("""
                        UPDATE refresh_tokens
                        SET revoked = TRUE
                        WHERE token_hash = :tokenHash
                        """)
                .param("tokenHash", tokenHash)
                .update();
    }
}

```

---

## File: `services/auth-service/src/main/java/com/ecommerce/auth/user/UserAccount.java`

```java
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

```

---

## File: `services/auth-service/src/main/java/com/ecommerce/auth/user/UserRepository.java`

```java
package com.ecommerce.auth.user;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserRepository {

    private final JdbcClient jdbcClient;

    public UserRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public boolean existsByEmail(String email) {
        Integer count = jdbcClient.sql(
                        "SELECT COUNT(*) FROM users WHERE email = :email"
                )
                .param("email", email)
                .query(Integer.class)
                .single();

        return count > 0;
    }

    public UserAccount save(UserAccount user) {
        jdbcClient.sql("""
                        INSERT INTO users (
                            id, email, password_hash, role, enabled,
                            created_at, updated_at
                        )
                        VALUES (
                            :id, :email, :passwordHash, :role, :enabled,
                            :createdAt, :updatedAt
                        )
                        """)
                .param("id", user.id())
                .param("email", user.email())
                .param("passwordHash", user.passwordHash())
                .param("role", user.role().name())
                .param("enabled", user.enabled())
                .param("createdAt", user.createdAt().atOffset(ZoneOffset.UTC))
                .param("updatedAt", user.updatedAt().atOffset(ZoneOffset.UTC))
                .update();

        return user;
    }

    public Optional<UserAccount> findByEmail(String email) {
        return jdbcClient.sql("""
                        SELECT id, email, password_hash, role, enabled,
                               created_at, updated_at
                        FROM users
                        WHERE email = :email
                        """)
                .param("email", email)
                .query((rs, rowNum) -> new UserAccount(
                        rs.getObject("id", UUID.class),
                        rs.getString("email"),
                        rs.getString("password_hash"),
                        UserRole.valueOf(rs.getString("role")),
                        rs.getBoolean("enabled"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ))
                .optional();
    }

    public Optional<UserAccount> findById(UUID id) {
        return jdbcClient.sql("""
                        SELECT id, email, password_hash, role, enabled,
                               created_at, updated_at
                        FROM users
                        WHERE id = :id
                        """)
                .param("id", id)
                .query((rs, rowNum) -> new UserAccount(
                        rs.getObject("id", UUID.class),
                        rs.getString("email"),
                        rs.getString("password_hash"),
                        UserRole.valueOf(rs.getString("role")),
                        rs.getBoolean("enabled"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ))
                .optional();
    }
}

```

---

## File: `services/auth-service/src/main/java/com/ecommerce/auth/user/UserRole.java`

```java
package com.ecommerce.auth.user;

public enum UserRole {
    CUSTOMER,
    ADMIN
}

```

---

## File: `services/auth-service/src/main/resources/application.properties`

```properties
spring.application.name=auth-service
server.port=8082

spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/auth_db}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME:ecommerce}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:ecommerce_dev_password}
spring.datasource.driver-class-name=org.postgresql.Driver

spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration

app.jwt.secret=${APP_JWT_SECRET:local-development-secret-change-this-to-a-long-random-value}
app.jwt.access-token-expiration-seconds=${APP_JWT_ACCESS_TOKEN_EXPIRATION_SECONDS:900}
app.jwt.refresh-token-expiration-seconds=${APP_JWT_REFRESH_TOKEN_EXPIRATION_SECONDS:604800}

management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=always

```

---

## File: `services/auth-service/src/main/resources/db/migration/V1__create_auth_tables.sql`

```sql
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL DEFAULT 'CUSTOMER',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    replaced_by UUID NULL
);

CREATE INDEX idx_refresh_tokens_user_id
    ON refresh_tokens(user_id);

CREATE INDEX idx_refresh_tokens_token_hash
    ON refresh_tokens(token_hash);

```

---

## File: `services/auth-service/src/test/java/com/ecommerce/auth/auth/AuthServiceTest.java`

```java
package com.ecommerce.auth.auth;

import com.ecommerce.auth.token.RefreshToken;
import com.ecommerce.auth.token.RefreshTokenRepository;
import com.ecommerce.auth.user.UserAccount;
import com.ecommerce.auth.user.UserRepository;
import com.ecommerce.auth.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private MockEnvironment environment;

    private AuthService authService;

    private UserAccount user;

    @BeforeEach
    void setUp() {
        environment = new MockEnvironment()
                .withProperty(
                        "app.jwt.refresh-token-expiration-seconds",
                        "604800"
                );

        authService = new AuthService(
                userRepository,
                refreshTokenRepository,
                passwordEncoder,
                jwtService,
                environment
        );

        user = new UserAccount(
                UUID.randomUUID(),
                "yogesh@example.com",
                "hashed-password",
                UserRole.CUSTOMER,
                true,
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    void registerShouldCreateCustomerWithHashedPassword() {
        when(userRepository.existsByEmail("yogesh@example.com"))
                .thenReturn(false);
        when(passwordEncoder.encode("StrongPassword123"))
                .thenReturn("hashed-password");
        when(userRepository.save(any(UserAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AuthDtos.UserResponse result = authService.register(
                new AuthDtos.RegisterRequest(
                        "YOGESH@EXAMPLE.COM",
                        "StrongPassword123"
                )
        );

        assertNotNull(result.id());
        assertEquals("yogesh@example.com", result.email());
        assertEquals(UserRole.CUSTOMER, result.role());
        verify(passwordEncoder).encode("StrongPassword123");
        verify(userRepository).save(any(UserAccount.class));
    }

    @Test
    void registerShouldRejectDuplicateEmail() {
        when(userRepository.existsByEmail("yogesh@example.com"))
                .thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.register(
                        new AuthDtos.RegisterRequest(
                                "yogesh@example.com",
                                "StrongPassword123"
                        )
                )
        );

        assertEquals(409, exception.getStatusCode().value());
    }

    @Test
    void loginShouldReturnAccessAndRefreshTokens() {
        when(userRepository.findByEmail("yogesh@example.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches(
                "StrongPassword123",
                "hashed-password"
        )).thenReturn(true);
        when(jwtService.createAccessToken(user))
                .thenReturn("access-token");
        when(jwtService.expirationSeconds())
                .thenReturn(900L);
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AuthDtos.TokenResponse result = authService.login(
                new AuthDtos.LoginRequest(
                        "yogesh@example.com",
                        "StrongPassword123"
                )
        );

        assertEquals("access-token", result.accessToken());
        assertNotNull(result.refreshToken());
        assertEquals("Bearer", result.tokenType());
        assertEquals(900L, result.expiresIn());
    }

    @Test
    void loginShouldRejectInvalidPassword() {
        when(userRepository.findByEmail("yogesh@example.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches(
                "wrong-password",
                "hashed-password"
        )).thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(
                        new AuthDtos.LoginRequest(
                                "yogesh@example.com",
                                "wrong-password"
                        )
                )
        );

        assertEquals(401, exception.getStatusCode().value());
    }

    @Test
    void loginShouldRejectUnknownEmail() {
        when(userRepository.findByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(
                        new AuthDtos.LoginRequest(
                                "missing@example.com",
                                "StrongPassword123"
                        )
                )
        );

        assertEquals(401, exception.getStatusCode().value());
    }

    @Test
    void logoutShouldRevokeRefreshToken() {
        authService.logout(
                new AuthDtos.LogoutRequest(
                        "refresh-id.raw-refresh-token"
                )
        );

        verify(refreshTokenRepository).revokeByHash(any(String.class));
    }
}

```

---

## File: `services/auth-service/src/test/java/com/ecommerce/auth/auth/JwtServiceTest.java`

```java
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

```

---

## File: `services/auth-service/src/test/java/com/ecommerce/auth/AuthServiceApplicationTests.java`

```java
package com.ecommerce.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AuthServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}

```
