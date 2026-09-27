package com.ecommerce.auth.auth;

import com.ecommerce.auth.token.RefreshToken;
import com.ecommerce.auth.token.RefreshTokenRepository;
import com.ecommerce.auth.user.UserAccount;
import com.ecommerce.auth.user.UserRepository;
import com.ecommerce.auth.user.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
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

        userRepository.save(user);

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

    public AuthDtos.TokenResponse refresh(AuthDtos.RefreshRequest request) {
        String rawToken = extractRawRefreshToken(request.refreshToken());
        String hash = hashToken(rawToken);

        RefreshToken oldToken = refreshTokenRepository.findActiveByHash(hash)
                .orElseThrow(this::invalidRefreshToken);

        if (oldToken.expiresAt().isBefore(Instant.now())) {
            throw invalidRefreshToken();
        }

        UserAccount user = userRepository.findById(oldToken.userId())
                .orElseThrow(this::invalidRefreshToken);

        AuthDtos.TokenResponse response = issueTokens(user);
        UUID replacementId = UUID.fromString(
                response.refreshToken().substring(0, 36)
        );

        refreshTokenRepository.revoke(oldToken.id(), replacementId);

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

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
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
