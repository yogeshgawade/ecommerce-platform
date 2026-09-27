package com.ecommerce.catalog.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    private Product product;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setId("product-1");
        product.setName("Running Shoes");
        product.setDescription("Lightweight running shoes");
        product.setCategory("footwear");
        product.setBrand("Acme");
        product.setPrice(new BigDecimal("2999.00"));
        product.setStockQuantity(25);
        product.setAttributes(Map.of("color", "black", "size", "10"));
    }

    @Test
    void createShouldSetTimestampsAndSaveProduct() {
        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Product result = productService.create(product);

        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());
        assertEquals("Running Shoes", result.getName());
        verify(productRepository).save(product);
    }

    @Test
    void findAllShouldReturnAllProducts() {
        when(productRepository.findAll()).thenReturn(List.of(product));

        List<Product> result = productService.findAll();

        assertEquals(1, result.size());
        assertEquals("product-1", result.get(0).getId());
        verify(productRepository).findAll();
    }

    @Test
    void findByIdShouldReturnProductWhenProductExists() {
        when(productRepository.findById("product-1"))
                .thenReturn(Optional.of(product));

        Product result = productService.findById("product-1");

        assertEquals("product-1", result.getId());
    }

    @Test
    void findByIdShouldThrowNotFoundWhenProductDoesNotExist() {
        when(productRepository.findById("missing"))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> productService.findById("missing")
        );

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void updateShouldPreserveIdAndCreatedAt() {
        Product existing = new Product();
        existing.setId("product-1");
        existing.setCreatedAt(product.getCreatedAt());

        when(productRepository.findById("product-1"))
                .thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Product result = productService.update("product-1", product);

        assertEquals("product-1", result.getId());
        assertEquals("Running Shoes", result.getName());
        assertEquals(new BigDecimal("2999.00"), result.getPrice());
        assertNotNull(result.getUpdatedAt());
        verify(productRepository).save(existing);
    }

    @Test
    void deleteShouldDeleteExistingProduct() {
        when(productRepository.existsById("product-1")).thenReturn(true);

        productService.delete("product-1");

        verify(productRepository).deleteById("product-1");
    }

    @Test
    void deleteShouldThrowNotFoundWhenProductDoesNotExist() {
        when(productRepository.existsById("missing")).thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> productService.delete("missing")
        );

        assertEquals(404, exception.getStatusCode().value());
        verify(productRepository, never()).deleteById(any());
    }

    @Test
    void searchShouldDelegateToRepository() {
        when(productRepository.findByNameContainingIgnoreCase("running"))
                .thenReturn(List.of(product));

        List<Product> result = productService.search("running");

        assertEquals(1, result.size());
        assertEquals("Running Shoes", result.get(0).getName());
        verify(productRepository)
                .findByNameContainingIgnoreCase("running");
    }
}
