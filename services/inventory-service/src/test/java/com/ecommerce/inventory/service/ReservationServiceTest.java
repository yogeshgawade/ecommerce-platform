package com.ecommerce.inventory.service;

import com.ecommerce.inventory.messaging.OrderEvent;
import com.ecommerce.inventory.messaging.OrderEventItem;
import com.ecommerce.inventory.model.Inventory;
import com.ecommerce.inventory.model.InventoryOutboxMessage;
import com.ecommerce.inventory.model.InventoryReservation;
import com.ecommerce.inventory.model.ReservationStatus;
import com.ecommerce.inventory.repository.InventoryOutboxRepository;
import com.ecommerce.inventory.repository.InventoryRepository;
import com.ecommerce.inventory.repository.InventoryReservationLineRepository;
import com.ecommerce.inventory.repository.InventoryReservationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private InventoryReservationRepository reservationRepository;
    @Mock
    private InventoryReservationLineRepository reservationLineRepository;
    @Mock
    private InventoryOutboxRepository outboxRepository;

    private ReservationService reservationService;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        reservationService = new ReservationService(
                inventoryRepository,
                reservationRepository,
                reservationLineRepository,
                outboxRepository,
                new ObjectMapper().findAndRegisterModules(),
                "inventory-events");
        inventory = new Inventory("product-1", 10);
        inventory.setId(1L);
    }

    @Test
    void reservesAllItemsAndWritesSuccessEventToOutbox() {
        when(reservationRepository.existsByOrderId("order-1")).thenReturn(false);
        when(reservationRepository.saveAndFlush(any(InventoryReservation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryRepository.findByProductIdForUpdate("product-1")).thenReturn(Optional.of(inventory));

        reservationService.reserveForOrder(orderEvent(4));

        assertEquals(4, inventory.getReservedQuantity());
        verify(inventoryRepository).saveAll(any());
        ArgumentCaptor<InventoryOutboxMessage> message = ArgumentCaptor.forClass(InventoryOutboxMessage.class);
        verify(outboxRepository).save(message.capture());
        assertEquals("inventory-events", message.getValue().getTopic());
        assertTrue(message.getValue().getPayload().contains("InventoryReserved"));
        verify(reservationLineRepository).saveAll(any());
    }

    @Test
    void insufficientStockRejectsWholeOrderWithoutChangingStock() {
        when(reservationRepository.existsByOrderId("order-1")).thenReturn(false);
        when(reservationRepository.saveAndFlush(any(InventoryReservation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryRepository.findByProductIdForUpdate("product-1")).thenReturn(Optional.of(inventory));

        reservationService.reserveForOrder(orderEvent(11));

        assertEquals(0, inventory.getReservedQuantity());
        verify(inventoryRepository, never()).saveAll(any());
        ArgumentCaptor<InventoryReservation> reservation = ArgumentCaptor.forClass(InventoryReservation.class);
        verify(reservationRepository).save(reservation.capture());
        assertEquals(ReservationStatus.REJECTED, reservation.getValue().getStatus());
        ArgumentCaptor<InventoryOutboxMessage> message = ArgumentCaptor.forClass(InventoryOutboxMessage.class);
        verify(outboxRepository).save(message.capture());
        assertTrue(message.getValue().getPayload().contains("InventoryReservationFailed"));
    }

    @Test
    void duplicateOrderEventDoesNotReserveTwice() {
        when(reservationRepository.existsByOrderId("order-1")).thenReturn(true);

        reservationService.reserveForOrder(orderEvent(4));

        verify(inventoryRepository, never()).findByProductIdForUpdate("product-1");
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void cancellationReleasesReservedStockOnce() {
        inventory.setReservedQuantity(4);
        InventoryReservation reservation = new InventoryReservation("order-1", ReservationStatus.RESERVED, null);
        when(reservationRepository.findByOrderId("order-1")).thenReturn(Optional.of(reservation));
        when(reservationLineRepository.findAllByOrderIdOrderByProductIdAsc("order-1"))
                .thenReturn(List.of(new com.ecommerce.inventory.model.InventoryReservationLine("order-1", "product-1", 4)));
        when(inventoryRepository.findByProductIdForUpdate("product-1")).thenReturn(Optional.of(inventory));

        reservationService.releaseForOrder("order-1");

        assertEquals(0, inventory.getReservedQuantity());
        assertEquals(ReservationStatus.RELEASED, reservation.getStatus());
    }

    @Test
    void confirmationDeductsStockAndReservation() {
        inventory.setReservedQuantity(4);
        InventoryReservation reservation = new InventoryReservation("order-1", ReservationStatus.RESERVED, null);
        when(reservationRepository.findByOrderId("order-1")).thenReturn(Optional.of(reservation));
        when(reservationLineRepository.findAllByOrderIdOrderByProductIdAsc("order-1"))
                .thenReturn(List.of(new com.ecommerce.inventory.model.InventoryReservationLine("order-1", "product-1", 4)));
        when(inventoryRepository.findByProductIdForUpdate("product-1")).thenReturn(Optional.of(inventory));

        reservationService.commitForOrder("order-1");

        assertEquals(6, inventory.getQuantity());
        assertEquals(0, inventory.getReservedQuantity());
        assertEquals(ReservationStatus.COMMITTED, reservation.getStatus());
    }

    private OrderEvent orderEvent(int quantity) {
        return new OrderEvent("OrderCreated", "event-1", "order-1", List.of(new OrderEventItem("product-1", quantity)));
    }
}