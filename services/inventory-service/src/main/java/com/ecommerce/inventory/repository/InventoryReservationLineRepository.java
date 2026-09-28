package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.model.InventoryReservationLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryReservationLineRepository extends JpaRepository<InventoryReservationLine, Long> {
    List<InventoryReservationLine> findAllByOrderIdOrderByProductIdAsc(String orderId);
}