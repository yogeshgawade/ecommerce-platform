package com.ecommerce.order.messaging;

import com.ecommerce.order.model.OrderOutboxMessage;
import com.ecommerce.order.repository.OrderOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class OrderOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OrderOutboxPublisher.class);
    private final OrderOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OrderOutboxPublisher(OrderOutboxRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${app.kafka.outbox-poll-interval:1000}")
    public void publishPending() {
        List<OrderOutboxMessage> pending = outboxRepository.findReadyToPublish(
                Instant.now(), PageRequest.of(0, 25));
        for (OrderOutboxMessage message : pending) {
            try {
                kafkaTemplate.send(message.getTopic(), message.getMessageKey(), message.getPayload())
                        .get(10, TimeUnit.SECONDS);
                message.markPublished();
                outboxRepository.save(message);
            } catch (Exception exception) {
                String error = exception.getMessage() == null
                        ? exception.getClass().getSimpleName() : exception.getMessage();
                message.markAttemptFailed(error);
                outboxRepository.save(message);
                log.warn("Could not publish order outbox message {}", message.getId(), exception);
            }
        }
    }
}
