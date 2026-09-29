package com.ecommerce.order.controller;

import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderPageResponse;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@Validated
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OrderResponse createOrder(Authentication authentication,
                                     @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
                                     @Valid @RequestBody CreateOrderRequest request) {
        Object details = authentication.getDetails();
        if (!(details instanceof String customerEmail) || customerEmail.isBlank()) {
            throw new AuthenticationCredentialsNotFoundException("Customer email claim is required");
        }
        return orderService.createOrder(authentication.getName(), customerEmail, idempotencyKey.trim(), request);
    }

    @GetMapping
    public OrderPageResponse listOrders(Authentication authentication,
                                        @RequestParam(defaultValue = "0") @Min(0) int page,
                                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                        @RequestParam(required = false) String userId) {
        return orderService.listOrders(authentication.getName(), isAdmin(authentication), userId, page, size);
    }

    @GetMapping("/{orderId}")
    public OrderResponse getOrder(Authentication authentication, @PathVariable String orderId) {
        return orderService.getOrder(orderId, authentication.getName(), isAdmin(authentication));
    }

    @PatchMapping("/{orderId}/cancel")
    public OrderResponse cancelOrder(Authentication authentication, @PathVariable String orderId) {
        return orderService.cancelOrder(orderId, authentication.getName(), isAdmin(authentication));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }
}
