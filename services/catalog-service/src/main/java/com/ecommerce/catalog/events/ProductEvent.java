package com.ecommerce.catalog.events;

import com.ecommerce.catalog.product.Product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ProductEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String productId,
        ProductSnapshot product
) {
    public static ProductEvent from(String type, Product product) {
        return new ProductEvent(
                UUID.randomUUID().toString(),
                type,
                Instant.now(),
                product.getId(),
                new ProductSnapshot(
                        product.getName(),
                        product.getDescription(),
                        product.getCategory(),
                        product.getBrand(),
                        product.getPrice(),
                        product.getAttributes()
                )
        );
    }

    public record ProductSnapshot(
            String name,
            String description,
            String category,
            String brand,
            BigDecimal price,
            Map<String, String> attributes
    ) {
    }
}
