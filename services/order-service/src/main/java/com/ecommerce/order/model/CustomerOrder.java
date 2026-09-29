package com.ecommerce.order.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "purchase_order")
public class CustomerOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true, length = 36)
    private String orderId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "customer_email", length = 254)
    private String customerEmail;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "payment_method_id", nullable = false)
    private String paymentMethodId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CustomerOrderItem> items = new ArrayList<>();

    protected CustomerOrder() {
    }

    public CustomerOrder(String orderId, String userId, String idempotencyKey, String requestHash,
                         OrderStatus status, BigDecimal totalAmount, String currency, String paymentMethodId) {
        this(orderId, userId, null, idempotencyKey, requestHash, status, totalAmount, currency, paymentMethodId);
    }

    public CustomerOrder(String orderId, String userId, String customerEmail, String idempotencyKey,
                         String requestHash, OrderStatus status, BigDecimal totalAmount,
                         String currency, String paymentMethodId) {
        this.orderId = orderId;
        this.userId = userId;
        this.customerEmail = customerEmail;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.status = status;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.paymentMethodId = paymentMethodId;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void addItem(String productId, String productName, int quantity,
                        BigDecimal unitPrice, BigDecimal lineTotal) {
        items.add(new CustomerOrderItem(this, productId, productName, quantity, unitPrice, lineTotal));
    }

    public boolean transition(OrderStatus expected, OrderStatus next) {
        if (status != expected) {
            return false;
        }
        status = next;
        updatedAt = Instant.now();
        return true;
    }

    public String getOrderId() { return orderId; }
    public String getUserId() { return userId; }
    public String getCustomerEmail() { return customerEmail; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCurrency() { return currency; }
    public String getPaymentMethodId() { return paymentMethodId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<CustomerOrderItem> getItems() { return List.copyOf(items); }
}
