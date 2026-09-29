package com.ecommerce.order.service;

import java.math.BigDecimal;

public record ResolvedOrderItem(String productId, String productName, int quantity, BigDecimal unitPrice) {
}
