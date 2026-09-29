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
