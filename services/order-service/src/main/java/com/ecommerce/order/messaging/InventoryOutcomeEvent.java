package com.ecommerce.order.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InventoryOutcomeEvent(
        String type,
        String eventId,
        Instant occurredAt,
        String orderId,
        List<OrderEventItem> items,
        String reason
) {
}
