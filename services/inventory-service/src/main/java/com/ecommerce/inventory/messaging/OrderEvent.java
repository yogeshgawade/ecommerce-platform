package com.ecommerce.inventory.messaging;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

public record OrderEvent(
        @JsonAlias("eventType") String type,
        String eventId,
        String orderId,
        List<OrderEventItem> items
) {
}