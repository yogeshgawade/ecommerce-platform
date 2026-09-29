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
