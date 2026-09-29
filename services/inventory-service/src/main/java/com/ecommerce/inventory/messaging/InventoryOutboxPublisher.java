package com.ecommerce.inventory.messaging;

import com.ecommerce.inventory.model.InventoryOutboxMessage;
import com.ecommerce.inventory.repository.InventoryOutboxRepository;
import org.springframework.data.domain.PageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
public class InventoryOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(InventoryOutboxPublisher.class);

    private final InventoryOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public InventoryOutboxPublisher(
            InventoryOutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${app.kafka.outbox-poll-interval:1000}")
    public void publishPending() {
        List<InventoryOutboxMessage> pending = outboxRepository
                .findReadyToPublish(Instant.now(), PageRequest.of(0, 25));
        for (InventoryOutboxMessage message : pending) {
            try {
                kafkaTemplate.send(message.getTopic(), message.getMessageKey(), message.getPayload())
                        .get(10, TimeUnit.SECONDS);
                message.markPublished();
                outboxRepository.save(message);
            } catch (Exception exception) {
                message.markAttemptFailed(exception.getMessage() == null
                        ? exception.getClass().getSimpleName()
                        : exception.getMessage());
                outboxRepository.save(message);
                log.warn("Could not publish inventory outbox message {}", message.getId(), exception);
            }
        }
    }
}
