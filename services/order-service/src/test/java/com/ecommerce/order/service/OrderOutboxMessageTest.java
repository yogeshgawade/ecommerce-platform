package com.ecommerce.order.service;

import com.ecommerce.order.model.OrderOutboxMessage;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderOutboxMessageTest {
    @Test
    void retryDelayGrowsAfterPublishFailure() {
        OrderOutboxMessage message = new OrderOutboxMessage("order-events", "order-1", "{}");

        Instant firstFailure = Instant.now();
        message.markAttemptFailed("broker unavailable");
        assertTrue(Duration.between(firstFailure, message.getNextAttemptAt()).toMillis() >= 1000);

        Instant secondFailure = Instant.now();
        message.markAttemptFailed("broker unavailable");
        assertTrue(Duration.between(secondFailure, message.getNextAttemptAt()).toMillis() >= 2000);
    }
}
