package com.ecommerce.order.service;

import com.ecommerce.order.dto.CatalogProductResponse;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderItemRequest;
import com.ecommerce.order.dto.OrderPageResponse;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.model.CustomerOrder;
import com.ecommerce.order.repository.CustomerOrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@Service
public class OrderService {
    private final CatalogClient catalogClient;
    private final OrderCommandService commandService;
    private final CustomerOrderRepository orderRepository;

    public OrderService(CatalogClient catalogClient, OrderCommandService commandService,
                        CustomerOrderRepository orderRepository) {
        this.catalogClient = catalogClient;
        this.commandService = commandService;
        this.orderRepository = orderRepository;
    }

    public OrderResponse createOrder(String userId, String customerEmail, String idempotencyKey,
                                     CreateOrderRequest request) {
        TreeMap<String, Integer> quantities = normalize(request.items());
        String paymentMethodId = request.paymentMethodId().trim();
        String requestHash = requestHash(quantities, paymentMethodId);
        Optional<OrderResponse> replay = commandService.findIdempotentReplay(userId, idempotencyKey, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }
        Map<String, CatalogProductResponse> products = new TreeMap<>();
        quantities.keySet().forEach(productId -> products.put(productId, catalogClient.getProduct(productId)));

        var resolvedItems = quantities.entrySet().stream().map(entry -> {
            CatalogProductResponse product = products.get(entry.getKey());
            if (product.price().signum() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Catalog returned a non-positive price for product " + entry.getKey());
            }
            return new ResolvedOrderItem(product.id(), product.name(), entry.getValue(),
                    product.price().setScale(2, java.math.RoundingMode.HALF_UP));
        }).toList();
        CustomerOrder order = customerEmail == null
                ? commandService.createOrder(userId, idempotencyKey, requestHash, paymentMethodId, resolvedItems)
                : commandService.createOrder(userId, customerEmail, idempotencyKey, requestHash,
                        paymentMethodId, resolvedItems);
        return OrderResponse.from(order);
    }

    public OrderResponse createOrder(String userId, String idempotencyKey, CreateOrderRequest request) {
        return createOrder(userId, null, idempotencyKey, request);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(String orderId, String userId, boolean admin) {
        CustomerOrder order = (admin ? orderRepository.findByOrderId(orderId)
                : orderRepository.findByOrderIdAndUserId(orderId, userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public OrderPageResponse listOrders(String userId, boolean admin, String requestedUserId,
                                        int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CustomerOrder> orders;
        if (admin && requestedUserId != null && !requestedUserId.isBlank()) {
            orders = orderRepository.findByUserIdOrderByCreatedAtDesc(requestedUserId, pageable);
        } else if (admin) {
            orders = orderRepository.findAllByOrderByCreatedAtDesc(pageable);
        } else {
            orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }
        return OrderPageResponse.from(orders.map(OrderResponse::from));
    }

    public OrderResponse cancelOrder(String orderId, String userId, boolean admin) {
        return OrderResponse.from(commandService.cancelOrder(orderId, userId, admin));
    }

    private TreeMap<String, Integer> normalize(java.util.List<OrderItemRequest> items) {
        if (items == null || items.isEmpty() || items.size() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An order must contain 1 to 50 items");
        }
        TreeMap<String, Integer> quantities = new TreeMap<>();
        try {
            for (OrderItemRequest item : items) {
                if (item == null || item.productId() == null || item.productId().isBlank()
                        || item.quantity() == null || item.quantity() < 1 || item.quantity() > 1000) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid order item");
                }
                quantities.merge(item.productId().trim(), item.quantity(), Math::addExact);
            }
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order quantity is too large", exception);
        }
        if (quantities.size() > 50 || quantities.values().stream().anyMatch(quantity -> quantity > 1000)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order quantity is too large");
        }
        return quantities;
    }

    private String requestHash(Map<String, Integer> quantities, String paymentMethodId) {
        String canonical = quantities.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(java.util.stream.Collectors.joining("|")) + "|" + paymentMethodId;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
