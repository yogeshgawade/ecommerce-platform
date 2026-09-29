package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.model.InventoryOutboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.time.Instant;

public interface InventoryOutboxRepository extends JpaRepository<InventoryOutboxMessage, Long> {
    @Query("""
            select message from InventoryOutboxMessage message
            where message.publishedAt is null
              and (message.nextAttemptAt is null or message.nextAttemptAt <= :now)
            order by message.createdAt asc, message.id asc
            """)
    List<InventoryOutboxMessage> findReadyToPublish(@Param("now") Instant now, Pageable pageable);
}
