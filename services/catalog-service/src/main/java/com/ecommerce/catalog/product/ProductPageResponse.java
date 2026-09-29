package com.ecommerce.catalog.product;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record ProductPageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <S, T> ProductPageResponse<T> from(Page<S> source, Function<S, T> mapper) {
        return new ProductPageResponse<>(
                source.getContent().stream().map(mapper).toList(),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages()
        );
    }
}
