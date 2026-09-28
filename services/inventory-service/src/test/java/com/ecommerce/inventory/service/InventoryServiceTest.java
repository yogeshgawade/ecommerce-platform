package com.ecommerce.inventory.service;

import com.ecommerce.inventory.model.Inventory;
import com.ecommerce.inventory.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private Inventory inventory;

    @BeforeEach
    void setUp() {
        inventory = new Inventory("product-1", 100);
        inventory.setId(1L);
    }

    @Test
    void findByProductIdShouldReturnInventory() {
        when(inventoryRepository.findByProductId("product-1"))
                .thenReturn(Optional.of(inventory));

        Inventory result = inventoryService.findByProductId("product-1");

        assertEquals("product-1", result.getProductId());
        assertEquals(100, result.getQuantity());
    }

    @Test
    void findByProductIdShouldThrowNotFound() {
        when(inventoryRepository.findByProductId("missing"))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.findByProductId("missing")
        );

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void reserveStockShouldReturnTrueWhenSufficientStock() {
        when(inventoryRepository.findByProductIdForUpdate("product-1"))
                .thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        boolean result = inventoryService.reserveStock("product-1", 10);

        assertTrue(result);
        assertEquals(10, inventory.getReservedQuantity());
        verify(inventoryRepository).save(inventory);
    }

    @Test
    void reserveStockShouldReturnFalseWhenInsufficientStock() {
        when(inventoryRepository.findByProductIdForUpdate("product-1"))
                .thenReturn(Optional.of(inventory));

        boolean result = inventoryService.reserveStock("product-1", 150);

        assertFalse(result);
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void releaseReservedStockShouldDecreaseReservedQuantity() {
        inventory.setReservedQuantity(20);
        when(inventoryRepository.findByProductIdForUpdate("product-1"))
                .thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        inventoryService.releaseReservedStock("product-1", 10);

        assertEquals(10, inventory.getReservedQuantity());
        verify(inventoryRepository).save(inventory);
    }

    @Test
    void deductStockShouldDecreaseQuantityAndReserved() {
        inventory.setReservedQuantity(20);
        when(inventoryRepository.findByProductIdForUpdate("product-1"))
                .thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        inventoryService.deductStock("product-1", 10);

        assertEquals(90, inventory.getQuantity());
        assertEquals(10, inventory.getReservedQuantity());
        verify(inventoryRepository).save(inventory);
    }
}
