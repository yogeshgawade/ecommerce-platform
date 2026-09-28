package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.model.InventoryReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {
    Optional<InventoryReservation> findByOrderId(String orderId);
    boolean existsByOrderId(String orderId);
}