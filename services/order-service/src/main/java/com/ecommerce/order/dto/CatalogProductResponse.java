package com.ecommerce.order.dto;

import java.math.BigDecimal;

public record CatalogProductResponse(String id, String name, BigDecimal price) {
}
