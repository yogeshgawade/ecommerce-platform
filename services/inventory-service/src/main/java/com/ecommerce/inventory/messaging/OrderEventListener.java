package com.ecommerce.inventory.messaging;

import com.ecommerce.inventory.service.ReservationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final ObjectMapper objectMapper;
    private final ReservationService reservationService;

    public OrderEventListener(ObjectMapper objectMapper, ReservationService reservationService) {
        this.objectMapper = objectMapper;
        this.reservationService = reservationService;
    }

    @KafkaListener(
            topics = "${app.kafka.order-topic:order-events}",
            groupId = "${spring.kafka.consumer.group-id:inventory-service-group}"
    )
    public void consume(String payload) {
        final OrderEvent event;
        try {
            event = objectMapper.readValue(payload, OrderEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid order event JSON", exception);
        }

        if (event.type() == null || event.orderId() == null || event.orderId().isBlank()) {
            throw new IllegalArgumentException("Order event requires type and orderId");
        }

        switch (event.type()) {
            case "OrderCreated" -> reservationService.reserveForOrder(event);
            case "OrderCancelled" -> reservationService.releaseForOrder(event.orderId());
            case "OrderConfirmed" -> reservationService.commitForOrder(event.orderId());
            default -> log.debug("Ignoring unsupported order event type {}", event.type());
        }
    }
}