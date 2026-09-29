package com.ecommerce.catalog.events;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ProductOutboxRepository extends MongoRepository<ProductOutboxMessage, String> {
    List<ProductOutboxMessage> findTop50ByPublishedAtIsNullOrderByCreatedAtAscIdAsc();
}
