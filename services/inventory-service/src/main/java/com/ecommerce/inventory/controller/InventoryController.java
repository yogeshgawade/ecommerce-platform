package com.ecommerce.inventory.controller;

import com.ecommerce.inventory.model.Inventory;
import com.ecommerce.inventory.service.InventoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@Validated
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public List<Inventory> findAll() {
        return inventoryService.findAll();
    }

    @GetMapping("/{productId}")
    public Inventory findByProductId(@PathVariable String productId) {
        return inventoryService.findByProductId(productId);
    }

    @PutMapping("/{productId}")
    public ResponseEntity<Inventory> createOrUpdate(
            @PathVariable String productId,
            @RequestBody @Min(0) Integer quantity
    ) {
        Inventory inventory = inventoryService.createOrUpdate(productId, quantity);
        return ResponseEntity.ok(inventory);
    }

    @PostMapping("/{productId}/reserve")
    public ResponseEntity<Boolean> reserveStock(
            @PathVariable String productId,
            @RequestParam @Positive Integer quantity
    ) {
        boolean success = inventoryService.reserveStock(productId, quantity);
        return ResponseEntity.ok(success);
    }

    @PostMapping("/{productId}/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void releaseReservedStock(
            @PathVariable String productId,
            @RequestParam @Positive Integer quantity
    ) {
        inventoryService.releaseReservedStock(productId, quantity);
    }

    @PostMapping("/{productId}/deduct")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deductStock(
            @PathVariable String productId,
            @RequestParam @Positive Integer quantity
    ) {
        inventoryService.deductStock(productId, quantity);
    }
}
