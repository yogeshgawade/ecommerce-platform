package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.model.InventoryOutboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryOutboxRepository extends JpaRepository<InventoryOutboxMessage, Long> {
    List<InventoryOutboxMessage> findTop25ByPublishedAtIsNullOrderByCreatedAtAscIdAsc();
}