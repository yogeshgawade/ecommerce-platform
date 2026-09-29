package com.ecommerce.inventory.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryOutboxMessageTest {

    @Test
    void failedPublishSchedulesExponentialRetry() {
        InventoryOutboxMessage message = new InventoryOutboxMessage("inventory-events", "order-1", "{}");

        Instant firstFailure = Instant.now();
        message.markAttemptFailed("broker unavailable");
        assertEquals(1, message.getAttempts());
        assertTrue(Duration.between(firstFailure, message.getNextAttemptAt()).toMillis() >= 1000);

        Instant secondFailure = Instant.now();
        message.markAttemptFailed("broker unavailable");
        assertEquals(2, message.getAttempts());
        assertTrue(Duration.between(secondFailure, message.getNextAttemptAt()).toMillis() >= 2000);
    }

    @Test
    void successfulPublishClearsRetryScheduleAndLastError() {
        InventoryOutboxMessage message = new InventoryOutboxMessage("inventory-events", "order-1", "{}");
        message.markAttemptFailed("broker unavailable");

        message.markPublished();

        assertTrue(message.getPublishedAt() != null);
        assertNull(message.getNextAttemptAt());
        assertNull(message.getLastError());
    }
}
