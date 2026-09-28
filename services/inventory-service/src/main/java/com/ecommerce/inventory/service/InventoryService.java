package com.ecommerce.inventory.service;

import com.ecommerce.inventory.model.Inventory;
import com.ecommerce.inventory.repository.InventoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    public List<Inventory> findAll() {
        return inventoryRepository.findAll();
    }

    public Inventory findByProductId(String productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Inventory not found for product: " + productId
                ));
    }

    @Transactional
    public Inventory createOrUpdate(String productId, Integer quantity) {
        requireProductId(productId);
        requireNonNegative(quantity);

        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseGet(() -> new Inventory(productId, quantity));
        if (quantity < inventory.getReservedQuantity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Stock cannot be reduced below the reserved quantity");
        }

        inventory.setQuantity(quantity);
        inventory.setLastUpdatedAt(Instant.now());
        return inventoryRepository.save(inventory);
    }

    @Transactional
    public boolean reserveStock(String productId, Integer quantity) {
        requirePositive(quantity);
        Inventory inventory = findForUpdate(productId);

        if (inventory.getAvailableQuantity() < quantity) {
            return false;
        }

        inventory.setReservedQuantity(inventory.getReservedQuantity() + quantity);
        inventory.setLastUpdatedAt(Instant.now());
        inventoryRepository.save(inventory);
        return true;
    }

    @Transactional
    public void releaseReservedStock(String productId, Integer quantity) {
        requirePositive(quantity);
        Inventory inventory = findForUpdate(productId);

        if (inventory.getReservedQuantity() < quantity) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot release more stock than is currently reserved");
        }

        inventory.setReservedQuantity(inventory.getReservedQuantity() - quantity);
        inventory.setLastUpdatedAt(Instant.now());
        inventoryRepository.save(inventory);
    }

    @Transactional
    public void deductStock(String productId, Integer quantity) {
        requirePositive(quantity);
        Inventory inventory = findForUpdate(productId);

        if (inventory.getReservedQuantity() < quantity) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot deduct more than reserved quantity"
            );
        }

        inventory.setQuantity(inventory.getQuantity() - quantity);
        inventory.setReservedQuantity(inventory.getReservedQuantity() - quantity);
        inventory.setLastUpdatedAt(Instant.now());
        inventoryRepository.save(inventory);
    }

    private Inventory findForUpdate(String productId) {
        requireProductId(productId);
        return inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Inventory not found for product: " + productId));
    }

    private void requireProductId(String productId) {
        if (productId == null || productId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "productId is required");
        }
    }

    private void requirePositive(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must be greater than zero");
        }
    }

    private void requireNonNegative(Integer quantity) {
        if (quantity == null || quantity < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must not be negative");
        }
    }
}
