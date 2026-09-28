package com.ecommerce.inventory.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "inventory_reservation")
public class InventoryReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ReservationStatus status;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InventoryReservation() {
    }

    public InventoryReservation(String orderId, ReservationStatus status, String failureReason) {
        this.orderId = orderId;
        this.status = status;
        this.failureReason = failureReason;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getOrderId() { return orderId; }
    public ReservationStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }

    public void transitionTo(ReservationStatus status, String failureReason) {
        this.status = status;
        this.failureReason = failureReason;
        this.updatedAt = Instant.now();
    }
}