package com.ecommerce.inventory.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InventoryEvent(
        String type,
        String eventId,
        Instant occurredAt,
        String orderId,
        List<OrderEventItem> items,
        String reason
) {
    public InventoryEvent(String type, String orderId, List<OrderEventItem> items, String reason) {
        this(type, UUID.randomUUID().toString(), Instant.now(), orderId, items, reason);
    }
}