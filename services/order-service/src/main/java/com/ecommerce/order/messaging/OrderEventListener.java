package com.ecommerce.order.messaging;

import com.ecommerce.order.service.OrderCommandService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {
    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);
    private final ObjectMapper objectMapper;
    private final OrderCommandService orderCommandService;

    public OrderEventListener(ObjectMapper objectMapper, OrderCommandService orderCommandService) {
        this.objectMapper = objectMapper;
        this.orderCommandService = orderCommandService;
    }

    @KafkaListener(topics = "${app.kafka.inventory-topic:inventory-events}",
            groupId = "${spring.kafka.consumer.group-id:order-service-group}")
    public void consumeInventoryEvent(String payload) {
        try {
            InventoryOutcomeEvent event = objectMapper.readValue(payload, InventoryOutcomeEvent.class);
            if (event.type() == null || event.orderId() == null || event.orderId().isBlank()) {
                throw new IllegalArgumentException("Inventory event requires type and orderId");
            }
            orderCommandService.handleInventoryEvent(event.type(), event.orderId(), event.reason());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid inventory event JSON", exception);
        }
    }

    @KafkaListener(topics = "${app.kafka.payment-topic:payment-events}",
            groupId = "${spring.kafka.consumer.group-id:order-service-group}")
    public void consumePaymentEvent(String payload) {
        try {
            PaymentEvent event = objectMapper.readValue(payload, PaymentEvent.class);
            if (event.type() == null || event.orderId() == null || event.orderId().isBlank()) {
                throw new IllegalArgumentException("Payment event requires type and orderId");
            }
            if (!"PaymentCompleted".equals(event.type()) && !"PaymentFailed".equals(event.type())) {
                log.debug("Ignoring unsupported payment event type {}", event.type());
                return;
            }
            orderCommandService.handlePaymentEvent(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid payment event JSON", exception);
        }
    }

}
