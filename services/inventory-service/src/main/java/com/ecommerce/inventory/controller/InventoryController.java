package com.ecommerce.inventory.controller;

import com.ecommerce.inventory.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

@RestController
@RequestMapping("/api/inventory")
@Validated
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public InventoryPageResponse findAll(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("productId").ascending());
        Page<InventoryResponse> response = inventoryService.findAll(pageable)
                .map(InventoryResponse::from);
        return InventoryPageResponse.from(response);
    }

    @GetMapping("/{productId}")
    public InventoryResponse findByProductId(@PathVariable String productId) {
        return InventoryResponse.from(inventoryService.findByProductId(productId));
    }

    @PutMapping("/{productId}")
    public ResponseEntity<InventoryResponse> createOrUpdate(
            @PathVariable String productId,
            @Valid @RequestBody InventoryQuantityRequest request
    ) {
        return ResponseEntity.ok(InventoryResponse.from(
                inventoryService.createOrUpdate(productId, request.quantity())));
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
