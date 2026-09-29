package com.ecommerce.catalog.events;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "product_outbox")
public class ProductOutboxMessage {

    @Id
    private String id;
    private String topic;
    private String messageKey;
    private String payload;
    private Instant createdAt;
    private Instant publishedAt;
    private int attempts;
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
