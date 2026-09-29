package com.ecommerce.order.dto;

import com.ecommerce.order.model.CustomerOrderItem;

import java.math.BigDecimal;

public record OrderItemResponse(
        String productId,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {
    public static OrderItemResponse from(CustomerOrderItem item) {
        return new OrderItemResponse(item.getProductId(), item.getProductName(), item.getQuantity(),
                item.getUnitPrice(), item.getLineTotal());
    }
}
