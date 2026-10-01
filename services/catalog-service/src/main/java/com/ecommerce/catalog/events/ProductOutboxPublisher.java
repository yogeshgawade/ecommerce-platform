package com.ecommerce.catalog.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class ProductOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(ProductOutboxPublisher.class);

    private final ProductOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final boolean kafkaEnabled;

    public ProductOutboxPublisher(
            ProductOutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${app.kafka.enabled:true}") boolean kafkaEnabled
    ) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaEnabled = kafkaEnabled;
    }

    @Scheduled(fixedDelayString = "${app.kafka.outbox-poll-interval:1000}")
    public void publishPending() {
        if (!kafkaEnabled) {
            return;
        }

        for (ProductOutboxMessage message : outboxRepository
                .findTop50ByPublishedAtIsNullOrderByCreatedAtAscIdAsc()) {
            try {
                kafkaTemplate.send(message.getTopic(), message.getMessageKey(), message.getPayload())
                        .get(10, TimeUnit.SECONDS);
                message.markPublished();
                outboxRepository.save(message);
            } catch (Exception exception) {
                String messageText = exception.getMessage() == null
                        ? exception.getClass().getSimpleName()
                        : exception.getMessage();
                message.markAttemptFailed(messageText);
                outboxRepository.save(message);
                log.warn("Could not publish product outbox message {}", message.getId(), exception);
            }
        }
    }
}
