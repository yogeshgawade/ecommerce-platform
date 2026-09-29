package com.ecommerce.order.messaging;

public record OrderEventItem(String productId, Integer quantity) {
}
