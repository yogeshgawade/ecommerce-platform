package com.ecommerce.order.messaging;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentEvent(
        @JsonAlias("eventType") String type,
        String eventId,
        Instant occurredAt,
        String orderId,
        BigDecimal amount,
        String currency,
        String providerReference,
        String reason
) {
}
