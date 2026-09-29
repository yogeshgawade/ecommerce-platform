package com.ecommerce.inventory.controller;

import org.springframework.data.domain.Page;

import java.util.List;

public record InventoryPageResponse(
        List<InventoryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static InventoryPageResponse from(Page<InventoryResponse> result) {
        return new InventoryPageResponse(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }
}
