package com.ecommerce.order.service;

import com.ecommerce.order.messaging.OrderEvent;
import com.ecommerce.order.messaging.OrderEventItem;
import com.ecommerce.order.messaging.PaymentEvent;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.model.CustomerOrder;
import com.ecommerce.order.model.OrderOutboxMessage;
import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.repository.CustomerOrderRepository;
import com.ecommerce.order.repository.OrderOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderCommandService {
    private final CustomerOrderRepository orderRepository;
    private final OrderOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final String orderTopic;
    private final String paymentTopic;

    public OrderCommandService(CustomerOrderRepository orderRepository, OrderOutboxRepository outboxRepository,
                               ObjectMapper objectMapper,
                               @Value("${app.kafka.order-topic:order-events}") String orderTopic,
                               @Value("${app.kafka.payment-topic:payment-events}") String paymentTopic) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.orderTopic = orderTopic;
        this.paymentTopic = paymentTopic;
    }

    @Transactional
    public CustomerOrder createOrder(String userId, String key, String requestHash,
                                     String paymentMethodId, List<ResolvedOrderItem> items) {
        return createOrder(userId, null, key, requestHash, paymentMethodId, items);
    }

    @Transactional
    public CustomerOrder createOrder(String userId, String customerEmail, String key, String requestHash,
                                     String paymentMethodId, List<ResolvedOrderItem> items) {
        return orderRepository.findByUserIdAndIdempotencyKey(userId, key)
                .map(existing -> verifyIdempotentReplay(existing, requestHash))
                .orElseGet(() -> createNewOrder(userId, customerEmail, key, requestHash, paymentMethodId, items));
    }

    @Transactional(readOnly = true)
    public Optional<OrderResponse> findIdempotentReplay(String userId, String key, String requestHash) {
        return orderRepository.findByUserIdAndIdempotencyKey(userId, key)
                .map(existing -> OrderResponse.from(verifyIdempotentReplay(existing, requestHash)));
    }

    private CustomerOrder createNewOrder(String userId, String customerEmail, String key, String requestHash,
                                         String paymentMethodId, List<ResolvedOrderItem> items) {
        BigDecimal total = items.stream()
                .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        CustomerOrder order = new CustomerOrder(UUID.randomUUID().toString(), userId, customerEmail, key, requestHash,
                OrderStatus.PENDING_INVENTORY, total, "USD", paymentMethodId);
        List<OrderEventItem> eventItems = items.stream()
                .map(item -> new OrderEventItem(item.productId(), item.quantity())).toList();
        items.forEach(item -> order.addItem(item.productId(), item.productName(), item.quantity(),
                item.unitPrice(), item.unitPrice().multiply(BigDecimal.valueOf(item.quantity()))
                        .setScale(2, java.math.RoundingMode.HALF_UP)));

        try {
            CustomerOrder saved = orderRepository.saveAndFlush(order);
            enqueue(orderTopic, saved.getOrderId(), orderEvent("OrderCreated", saved, eventItems, null));
            return saved;
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An order with this idempotency key was submitted concurrently; retry the request", exception);
        }
    }

    private CustomerOrder verifyIdempotentReplay(CustomerOrder existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Idempotency-Key was already used with a different order request");
        }
        return existing;
    }

    @Transactional
    public CustomerOrder cancelOrder(String orderId, String userId, boolean admin) {
        CustomerOrder order = getForUser(orderId, userId, admin);
        if (!order.transition(OrderStatus.PENDING_INVENTORY, OrderStatus.CANCELLED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only orders awaiting inventory reservation can be cancelled");
        }
        enqueue(orderTopic, order.getOrderId(), orderEvent("OrderCancelled", order, null, "Cancelled by customer"));
        return order;
    }

    @Transactional
    public void handleInventoryEvent(String type, String orderId, String reason) {
        CustomerOrder order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory event references unknown order"));
        if ("InventoryReserved".equals(type)
                && order.transition(OrderStatus.PENDING_INVENTORY, OrderStatus.PENDING_PAYMENT)) {
            enqueue(paymentTopic, order.getOrderId(), new PaymentRequested(
                    "PaymentRequested", UUID.randomUUID().toString(), Instant.now(), order.getOrderId(),
                    order.getUserId(), order.getTotalAmount(), order.getCurrency(), order.getPaymentMethodId()));
        } else if ("InventoryReservationFailed".equals(type)
                && order.transition(OrderStatus.PENDING_INVENTORY, OrderStatus.CANCELLED)) {
            enqueue(orderTopic, order.getOrderId(), orderEvent("OrderCancelled", order,
                    null, safeReason(reason, "Inventory reservation failed")));
        }
    }

    @Transactional
    public void handlePaymentEvent(PaymentEvent event) {
        CustomerOrder order = orderRepository.findByOrderId(event.orderId())
                .orElseThrow(() -> new IllegalArgumentException("Payment event references unknown order"));
        if (event.amount() == null || event.amount().compareTo(order.getTotalAmount()) != 0
                || event.currency() == null || !event.currency().equalsIgnoreCase(order.getCurrency())) {
            throw new IllegalArgumentException("Payment event amount or currency does not match order");
        }

        if ("PaymentCompleted".equals(event.type())
                && order.transition(OrderStatus.PENDING_PAYMENT, OrderStatus.CONFIRMED)) {
            enqueue(orderTopic, order.getOrderId(), orderEvent("OrderConfirmed", order, null, null));
        } else if ("PaymentFailed".equals(event.type())
                && order.transition(OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED)) {
            enqueue(orderTopic, order.getOrderId(), orderEvent("OrderCancelled", order,
                    null, safeReason(event.reason(), "Payment failed")));
        }
    }

    private CustomerOrder getForUser(String orderId, String userId, boolean admin) {
        return (admin ? orderRepository.findByOrderId(orderId)
                : orderRepository.findByOrderIdAndUserId(orderId, userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private OrderEvent orderEvent(String type, CustomerOrder order, List<OrderEventItem> items, String reason) {
        return new OrderEvent(type, UUID.randomUUID().toString(), Instant.now(), order.getOrderId(),
                order.getUserId(), order.getCustomerEmail(), order.getTotalAmount(),
                order.getCurrency(), null, items, reason);
    }

    private String safeReason(String reason, String fallback) {
        if (reason == null || reason.isBlank()) {
            return fallback;
        }
        return reason.length() > 1000 ? reason.substring(0, 1000) : reason;
    }

    private void enqueue(String topic, String key, Object event) {
        try {
            outboxRepository.save(new OrderOutboxMessage(topic, key, objectMapper.writeValueAsString(event)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize order event", exception);
        }
    }

    private record PaymentRequested(String type, String eventId, Instant occurredAt, String orderId,
                                    String userId, BigDecimal amount, String currency,
                                    String paymentMethodId) {
    }
}
