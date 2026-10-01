package com.ecommerce.catalog.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductRepository extends JpaRepository<Product, String> {

    Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
