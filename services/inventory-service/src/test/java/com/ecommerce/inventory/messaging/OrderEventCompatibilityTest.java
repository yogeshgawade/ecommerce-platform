package com.ecommerce.inventory.messaging;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderEventCompatibilityTest {
    @Test
    void acceptsOrderLifecycleMetadataAddedByOrderService() throws Exception {
        String payload = """
                {"type":"OrderCreated","eventId":"event-1","occurredAt":"2026-09-29T00:00:00Z",
                 "orderId":"order-1","userId":"user-1","totalAmount":12.50,"currency":"USD",
                 "items":[{"productId":"product-1","quantity":1}]}
                """;

        OrderEvent event = JsonMapper.builder().findAndAddModules().build().readValue(payload, OrderEvent.class);

        assertEquals("OrderCreated", event.type());
        assertEquals("order-1", event.orderId());
        assertEquals("product-1", event.items().getFirst().productId());
    }
}
