package com.ecommerce.order.service;

import com.ecommerce.order.dto.CatalogProductResponse;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderItemRequest;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.model.CustomerOrder;
import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.repository.CustomerOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock
    private CatalogClient catalogClient;
    @Mock
    private OrderCommandService commandService;
    @Mock
    private CustomerOrderRepository orderRepository;

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(catalogClient, commandService, orderRepository);
    }

    @Test
    void usesCatalogPriceAndCombinesDuplicateProducts() {
        when(commandService.findIdempotentReplay(eq("user-1"), eq("checkout-1"), any(String.class)))
                .thenReturn(Optional.empty());
        when(catalogClient.getProduct("product-1"))
                .thenReturn(new CatalogProductResponse("product-1", "Shoes", new BigDecimal("12.50")));
        CustomerOrder saved = new CustomerOrder("order-1", "user-1", "checkout-1", "a".repeat(64),
                OrderStatus.PENDING_INVENTORY, new BigDecimal("37.50"), "USD", "pm_test");
        saved.addItem("product-1", "Shoes", 3, new BigDecimal("12.50"), new BigDecimal("37.50"));
        when(commandService.createOrder(any(), any(), any(), any(), anyList())).thenReturn(saved);

        OrderResponse result = service.createOrder("user-1", "checkout-1", new CreateOrderRequest(
                List.of(new OrderItemRequest("product-1", 1), new OrderItemRequest("product-1", 2)),
                "pm_test"));

        assertEquals(new BigDecimal("37.50"), result.totalAmount());
        assertEquals(3, result.items().getFirst().quantity());
        verify(commandService).createOrder(any(), any(), any(), any(), argThat(items ->
                items.size() == 1 && items.getFirst().quantity() == 3
                        && items.getFirst().unitPrice().equals(new BigDecimal("12.50"))));
    }

    @Test
    void idempotentReplayDoesNotDependOnCatalogAvailability() {
        OrderResponse prior = new OrderResponse("order-1", "user-1", OrderStatus.PENDING_PAYMENT,
                new BigDecimal("10.00"), "USD", List.of(), null, null);
        when(commandService.findIdempotentReplay(eq("user-1"), eq("checkout-1"), any(String.class)))
                .thenReturn(Optional.of(prior));

        OrderResponse result = service.createOrder("user-1", "checkout-1", new CreateOrderRequest(
                List.of(new OrderItemRequest("deleted-product", 1)), "pm_test"));

        assertEquals("order-1", result.orderId());
        verify(catalogClient, never()).getProduct("deleted-product");
        verify(commandService, never()).createOrder(any(), any(), any(), any(), anyList());
    }
}
