package com.ecommerce.inventory.controller;

import com.ecommerce.inventory.model.Inventory;

import java.time.Instant;

public record InventoryResponse(
        String productId,
        int quantity,
        int reservedQuantity,
        int availableQuantity,
        Instant lastUpdatedAt
) {
    public static InventoryResponse from(Inventory inventory) {
        return new InventoryResponse(
                inventory.getProductId(),
                inventory.getQuantity(),
                inventory.getReservedQuantity(),
                inventory.getAvailableQuantity(),
                inventory.getLastUpdatedAt()
        );
    }
}
