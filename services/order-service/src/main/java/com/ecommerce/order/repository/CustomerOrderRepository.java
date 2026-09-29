package com.ecommerce.order.repository;

import com.ecommerce.order.model.CustomerOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
    Optional<CustomerOrder> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);

    @EntityGraph(attributePaths = "items")
    Optional<CustomerOrder> findByOrderIdAndUserId(String orderId, String userId);

    @EntityGraph(attributePaths = "items")
    Optional<CustomerOrder> findByOrderId(String orderId);

    Page<CustomerOrder> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Page<CustomerOrder> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
