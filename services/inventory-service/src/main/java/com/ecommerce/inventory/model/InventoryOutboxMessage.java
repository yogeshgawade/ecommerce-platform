package com.ecommerce.inventory.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "inventory_outbox")
public class InventoryOutboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String topic;

    @Column(name = "message_key", nullable = false)
    private String messageKey;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private Integer attempts;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    protected InventoryOutboxMessage() {
    }

    public InventoryOutboxMessage(String topic, String messageKey, String payload) {
        this.topic = topic;
        this.messageKey = messageKey;
        this.payload = payload;
        this.createdAt = Instant.now();
        this.attempts = 0;
    }

    public Long getId() { return id; }
    public String getTopic() { return topic; }
    public String getMessageKey() { return messageKey; }
    public String getPayload() { return payload; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getLastError() { return lastError; }
    public Integer getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }

    public void markPublished() {
        this.publishedAt = Instant.now();
        this.nextAttemptAt = null;
        this.lastError = null;
    }

    public void markAttemptFailed(String error) {
        this.attempts++;
        this.lastError = error.length() > 2000 ? error.substring(0, 2000) : error;
        long retryDelayMillis = Math.min(300_000L, 1_000L << Math.min(attempts - 1, 9));
        this.nextAttemptAt = Instant.now().plusMillis(retryDelayMillis);
    }
}
