package com.ecommerce.inventory.messaging;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderEvent(
        @JsonAlias("eventType") String type,
        String eventId,
        String orderId,
        List<OrderEventItem> items
) {
}
