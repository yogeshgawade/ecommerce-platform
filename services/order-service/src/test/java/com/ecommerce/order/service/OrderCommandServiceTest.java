package com.ecommerce.order.service;

import com.ecommerce.order.messaging.PaymentEvent;
import com.ecommerce.order.model.CustomerOrder;
import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.repository.CustomerOrderRepository;
import com.ecommerce.order.repository.OrderOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCommandServiceTest {
    @Mock
    private CustomerOrderRepository orderRepository;
    @Mock
    private OrderOutboxRepository outboxRepository;

    private OrderCommandService service;

    @BeforeEach
    void setUp() {
        service = new OrderCommandService(orderRepository, outboxRepository,
                new ObjectMapper().findAndRegisterModules(), "order-events", "payment-events");
    }

    @Test
    void createsOrderAndAtomicallyQueuesInventoryEvent() throws Exception {
        when(orderRepository.findByUserIdAndIdempotencyKey("user-1", "checkout-1"))
                .thenReturn(Optional.empty());
        when(orderRepository.saveAndFlush(any(CustomerOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CustomerOrder order = service.createOrder("user-1", "customer@example.com", "checkout-1", "a".repeat(64),
                "pm_test", List.of(new ResolvedOrderItem("p-1", "Shoes", 2, new BigDecimal("12.50"))));

        assertEquals(OrderStatus.PENDING_INVENTORY, order.getStatus());
        assertEquals(new BigDecimal("25.00"), order.getTotalAmount());
        ArgumentCaptor<com.ecommerce.order.model.OrderOutboxMessage> captor =
                ArgumentCaptor.forClass(com.ecommerce.order.model.OrderOutboxMessage.class);
        verify(outboxRepository).save(captor.capture());
        var event = new ObjectMapper().readTree(captor.getValue().getPayload());
        assertEquals("OrderCreated", event.get("type").asText());
        assertEquals(order.getOrderId(), event.get("orderId").asText());
        assertEquals("customer@example.com", event.get("customerEmail").asText());
        assertEquals(2, event.get("items").get(0).get("quantity").asInt());
    }

    @Test
    void rejectsIdempotencyKeyReusedForDifferentRequest() {
        CustomerOrder prior = new CustomerOrder("order-1", "user-1", "checkout-1", "b".repeat(64),
                OrderStatus.PENDING_INVENTORY, new BigDecimal("10.00"), "USD", "pm_test");
        when(orderRepository.findByUserIdAndIdempotencyKey("user-1", "checkout-1"))
                .thenReturn(Optional.of(prior));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.createOrder("user-1", "checkout-1", "a".repeat(64), "pm_test", List.of()));

        assertEquals(409, exception.getStatusCode().value());
    }

    @Test
    void inventorySuccessMovesOrderToPaymentAndQueuesPaymentRequest() {
        CustomerOrder order = new CustomerOrder("order-1", "user-1", "checkout-1", "a".repeat(64),
                OrderStatus.PENDING_INVENTORY, new BigDecimal("19.99"), "USD", "pm_test");
        when(orderRepository.findByOrderId("order-1")).thenReturn(Optional.of(order));

        service.handleInventoryEvent("InventoryReserved", "order-1", null);

        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        ArgumentCaptor<com.ecommerce.order.model.OrderOutboxMessage> captor =
                ArgumentCaptor.forClass(com.ecommerce.order.model.OrderOutboxMessage.class);
        verify(outboxRepository).save(captor.capture());
        assertEquals("payment-events", captor.getValue().getTopic());
    }

    @Test
    void paymentSuccessConfirmsOrderAndQueuesInventoryCommit() throws Exception {
        CustomerOrder order = new CustomerOrder("order-1", "user-1", "customer@example.com",
                "checkout-1", "a".repeat(64), OrderStatus.PENDING_PAYMENT,
                new BigDecimal("19.99"), "USD", "pm_test");
        when(orderRepository.findByOrderId("order-1")).thenReturn(Optional.of(order));

        service.handlePaymentEvent(new PaymentEvent("PaymentCompleted", "event-1", null, "order-1",
                new BigDecimal("19.99"), "USD", "pi_test", null));

        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        ArgumentCaptor<com.ecommerce.order.model.OrderOutboxMessage> captor =
                ArgumentCaptor.forClass(com.ecommerce.order.model.OrderOutboxMessage.class);
        verify(outboxRepository).save(captor.capture());
        assertEquals("order-events", captor.getValue().getTopic());
        assertEquals("customer@example.com",
                new ObjectMapper().readTree(captor.getValue().getPayload()).get("customerEmail").asText());
    }
}
