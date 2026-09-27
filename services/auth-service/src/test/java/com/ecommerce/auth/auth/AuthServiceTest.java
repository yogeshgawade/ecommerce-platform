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
