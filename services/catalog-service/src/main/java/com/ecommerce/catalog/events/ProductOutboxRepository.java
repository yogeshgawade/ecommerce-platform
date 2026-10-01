package com.ecommerce.catalog.events;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductOutboxRepository extends JpaRepository<ProductOutboxMessage, String> {
    List<ProductOutboxMessage> findTop50ByPublishedAtIsNullOrderByCreatedAtAscIdAsc();
}
