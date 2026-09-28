package com.ecommerce.inventory.service;

import com.ecommerce.inventory.messaging.InventoryEvent;
import com.ecommerce.inventory.messaging.OrderEvent;
import com.ecommerce.inventory.messaging.OrderEventItem;
import com.ecommerce.inventory.model.Inventory;
import com.ecommerce.inventory.model.InventoryOutboxMessage;
import com.ecommerce.inventory.model.InventoryReservation;
import com.ecommerce.inventory.model.InventoryReservationLine;
import com.ecommerce.inventory.model.ReservationStatus;
import com.ecommerce.inventory.repository.InventoryOutboxRepository;
import com.ecommerce.inventory.repository.InventoryRepository;
import com.ecommerce.inventory.repository.InventoryReservationLineRepository;
import com.ecommerce.inventory.repository.InventoryReservationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class ReservationService {

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;
    private final InventoryReservationLineRepository reservationLineRepository;
    private final InventoryOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final String inventoryTopic;

    public ReservationService(
            InventoryRepository inventoryRepository,
            InventoryReservationRepository reservationRepository,
            InventoryReservationLineRepository reservationLineRepository,
            InventoryOutboxRepository outboxRepository,
            ObjectMapper objectMapper,
            @Value("${app.kafka.inventory-topic:inventory-events}") String inventoryTopic
    ) {
        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
        this.reservationLineRepository = reservationLineRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.inventoryTopic = inventoryTopic;
    }

    @Transactional
    public void reserveForOrder(OrderEvent event) {
        requireOrderId(event.orderId());
        if (reservationRepository.existsByOrderId(event.orderId())) {
            return;
        }

        TreeMap<String, Integer> quantities = aggregateAndValidate(event.items());
        List<OrderEventItem> normalizedItems = toEventItems(quantities);
        InventoryReservation reservation = reservationRepository.saveAndFlush(
                new InventoryReservation(event.orderId(), ReservationStatus.RESERVED, null));
        List<InventoryReservationLine> reservationLines = quantities.entrySet().stream()
                .map(line -> new InventoryReservationLine(event.orderId(), line.getKey(), line.getValue()))
                .toList();

        Map<String, Inventory> lockedInventory = new TreeMap<>();
        String failureReason = null;
        for (Map.Entry<String, Integer> line : quantities.entrySet()) {
            Inventory inventory = inventoryRepository.findByProductIdForUpdate(line.getKey()).orElse(null);
            if (inventory == null) {
                failureReason = "No inventory exists for product " + line.getKey();
                break;
            }
            if (inventory.getAvailableQuantity() < line.getValue()) {
                failureReason = "Insufficient stock for product " + line.getKey();
                break;
            }
            lockedInventory.put(line.getKey(), inventory);
        }

        reservationLineRepository.saveAll(reservationLines);
        if (failureReason != null) {
            reservation.transitionTo(ReservationStatus.REJECTED, failureReason);
            reservationRepository.save(reservation);
            enqueue(new InventoryEvent("InventoryReservationFailed", event.orderId(), normalizedItems, failureReason));
            return;
        }

        quantities.forEach((productId, quantity) -> {
            Inventory inventory = lockedInventory.get(productId);
            inventory.setReservedQuantity(inventory.getReservedQuantity() + quantity);
        });
        inventoryRepository.saveAll(lockedInventory.values());
        enqueue(new InventoryEvent("InventoryReserved", event.orderId(), normalizedItems, null));
    }

    @Transactional
    public void releaseForOrder(String orderId) {
        InventoryReservation reservation = findReservation(orderId);
        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            return;
        }

        List<InventoryReservationLine> lines = reservationLineRepository.findAllByOrderIdOrderByProductIdAsc(orderId);
        Map<String, Inventory> lockedInventory = lockInventory(lines);
        for (InventoryReservationLine line : lines) {
            Inventory inventory = lockedInventory.get(line.getProductId());
            if (inventory.getReservedQuantity() < line.getQuantity()) {
                throw new IllegalStateException("Reserved quantity is inconsistent for product " + line.getProductId());
            }
            inventory.setReservedQuantity(inventory.getReservedQuantity() - line.getQuantity());
        }
        inventoryRepository.saveAll(lockedInventory.values());
        reservation.transitionTo(ReservationStatus.RELEASED, null);
        reservationRepository.save(reservation);
    }

    @Transactional
    public void commitForOrder(String orderId) {
        InventoryReservation reservation = findReservation(orderId);
        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            return;
        }

        List<InventoryReservationLine> lines = reservationLineRepository.findAllByOrderIdOrderByProductIdAsc(orderId);
        Map<String, Inventory> lockedInventory = lockInventory(lines);
        for (InventoryReservationLine line : lines) {
            Inventory inventory = lockedInventory.get(line.getProductId());
            if (inventory.getReservedQuantity() < line.getQuantity() || inventory.getQuantity() < line.getQuantity()) {
                throw new IllegalStateException("Reserved quantity is inconsistent for product " + line.getProductId());
            }
            inventory.setQuantity(inventory.getQuantity() - line.getQuantity());
            inventory.setReservedQuantity(inventory.getReservedQuantity() - line.getQuantity());
        }
        inventoryRepository.saveAll(lockedInventory.values());
        reservation.transitionTo(ReservationStatus.COMMITTED, null);
        reservationRepository.save(reservation);
    }

    private Map<String, Inventory> lockInventory(List<InventoryReservationLine> lines) {
        Map<String, Inventory> lockedInventory = new TreeMap<>();
        for (InventoryReservationLine line : lines) {
            Inventory inventory = inventoryRepository.findByProductIdForUpdate(line.getProductId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Inventory disappeared for product " + line.getProductId()));
            lockedInventory.put(line.getProductId(), inventory);
        }
        return lockedInventory;
    }

    private InventoryReservation findReservation(String orderId) {
        requireOrderId(orderId);
        return reservationRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No inventory reservation exists for order " + orderId));
    }

    private TreeMap<String, Integer> aggregateAndValidate(List<OrderEventItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("OrderCreated requires at least one item");
        }
        TreeMap<String, Integer> quantities = new TreeMap<>();
        for (OrderEventItem item : items) {
            if (item == null || item.productId() == null || item.productId().isBlank()
                    || item.quantity() == null || item.quantity() <= 0) {
                throw new IllegalArgumentException("Each order item requires productId and a positive quantity");
            }
            quantities.merge(item.productId(), item.quantity(), Math::addExact);
        }
        return quantities;
    }

    private List<OrderEventItem> toEventItems(Map<String, Integer> quantities) {
        List<OrderEventItem> items = new ArrayList<>(quantities.size());
        quantities.forEach((productId, quantity) -> items.add(new OrderEventItem(productId, quantity)));
        return List.copyOf(items);
    }

    private void enqueue(InventoryEvent event) {
        try {
            outboxRepository.save(new InventoryOutboxMessage(
                    inventoryTopic,
                    event.orderId(),
                    objectMapper.writeValueAsString(event)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize inventory event", exception);
        }
    }

    private void requireOrderId(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId is required");
        }
    }
}