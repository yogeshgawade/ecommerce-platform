package com.ecommerce.order.messaging;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderEvent(
        @JsonAlias("eventType") String type,
        String eventId,
        Instant occurredAt,
        String orderId,
        String userId,
        String customerEmail,
        BigDecimal totalAmount,
        String currency,
        String paymentMethodId,
        List<OrderEventItem> items,
        String reason
) {
}
