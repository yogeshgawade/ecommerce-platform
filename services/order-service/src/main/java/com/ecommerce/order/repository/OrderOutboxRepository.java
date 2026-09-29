package com.ecommerce.order.repository;

import com.ecommerce.order.model.OrderOutboxMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OrderOutboxRepository extends JpaRepository<OrderOutboxMessage, Long> {
    @Query("""
            select message from OrderOutboxMessage message
            where message.publishedAt is null
              and (message.nextAttemptAt is null or message.nextAttemptAt <= :now)
            order by message.createdAt asc, message.id asc
            """)
    List<OrderOutboxMessage> findReadyToPublish(@Param("now") Instant now, Pageable pageable);
}
