package com.ecommerce.catalog.events;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "product_outbox", indexes = {
        @Index(name = "idx_product_outbox_pending", columnList = "published_at, created_at, id")
})
public class ProductOutboxMessage {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(nullable = false, length = 200)
    private String topic;

    @Column(name = "message_key", nullable = false, length = 200)
    private String messageKey;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    protected ProductOutboxMessage() {
    }

    public ProductOutboxMessage(String id, String topic, String messageKey, String payload) {
        this.id = id;
        this.topic = topic;
        this.messageKey = messageKey;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getTopic() { return topic; }
    public String getMessageKey() { return messageKey; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public int getAttempts() { return attempts; }
    public String getLastError() { return lastError; }

    public void markPublished() {
        publishedAt = Instant.now();
        lastError = null;
    }

    public void markAttemptFailed(String error) {
        attempts++;
        lastError = error.length() > 2000 ? error.substring(0, 2000) : error;
    }
}
