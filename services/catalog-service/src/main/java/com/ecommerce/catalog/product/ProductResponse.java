package com.ecommerce.catalog.product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record ProductResponse(
        String id,
        String name,
        String description,
        String category,
        String brand,
        BigDecimal price,
        Map<String, String> attributes,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getCategory(),
                product.getBrand(),
                product.getPrice(),
                product.getAttributes(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
