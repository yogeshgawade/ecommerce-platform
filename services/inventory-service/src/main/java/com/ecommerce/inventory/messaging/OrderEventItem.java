package com.ecommerce.inventory.messaging;

public record OrderEventItem(String productId, Integer quantity) {
}