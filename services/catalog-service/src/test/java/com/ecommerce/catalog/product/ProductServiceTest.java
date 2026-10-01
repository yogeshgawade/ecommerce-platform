package com.ecommerce.catalog.product;

import com.ecommerce.catalog.events.ProductOutboxMessage;
import com.ecommerce.catalog.events.ProductOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductServiceTest {

    private ProductRepository productRepository;
    private ProductOutboxRepository outboxRepository;
    private ObjectMapper objectMapper;
    private ProductService productService;
    private Product product;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        outboxRepository = mock(ProductOutboxRepository.class);
        objectMapper = mock(ObjectMapper.class);
        productService = new ProductService(productRepository, outboxRepository, objectMapper, "catalog-events");
        product = product();
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"eventType\":\"product\"}");
    }

    @Test
    void createSetsServerTimestampsAndWritesCreatedEvent() {
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse result = productService.create(request());

        assertDoesNotThrow(() -> UUID.fromString(result.id()));
        assertNotNull(result.createdAt());
        assertEquals(result.createdAt(), result.updatedAt());
        ArgumentCaptor<Product> product = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(product.capture());
        assertEquals(result.id(), product.getValue().getId());
        verify(outboxRepository).save(argThat(message ->
                message.getTopic().equals("catalog-events")
                        && message.getMessageKey().equals(result.id())));
    }

    @Test
    void createGeneratesAnIdBeforeSavingAndDoesNotAcceptStockFromRequest() {
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            assertDoesNotThrow(() -> UUID.fromString(saved.getId()));
            return saved;
        });

        productService.create(request());

        verify(productRepository).save(any(Product.class));
    }

    @Test
    void findAllReturnsPagedProducts() {
        when(productRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product), PageRequest.of(0, 20), 35));

        ProductPageResponse<ProductResponse> result = productService.findAll(0, 20);

        assertEquals(1, result.content().size());
        assertEquals(35, result.totalElements());
        assertEquals(2, result.totalPages());
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAll(pageable.capture());
        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(20, pageable.getValue().getPageSize());
        assertEquals(Sort.Direction.DESC,
                pageable.getValue().getSort().getOrderFor("createdAt").getDirection());
    }

    @Test
    void rejectsOutOfRangePageSize() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> productService.findAll(0, 101)
        );
        assertEquals(400, exception.getStatusCode().value());
        verifyNoInteractions(productRepository);
    }

    @Test
    void findByIdReturnsProductWhenItExists() {
        when(productRepository.findById("product-1")).thenReturn(Optional.of(product));

        assertEquals("product-1", productService.findById("product-1").id());
    }

    @Test
    void findByIdThrowsNotFoundWhenProductDoesNotExist() {
        when(productRepository.findById("missing")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> productService.findById("missing")
        );
        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void updatePreservesIdAndCreatedAtAndWritesUpdatedEvent() {
        when(productRepository.findById("product-1")).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse result = productService.update("product-1", request());

        assertEquals("product-1", result.id());
        assertEquals("2026-01-01T00:00:00Z", result.createdAt().toString());
        assertNotNull(result.updatedAt());
        verify(outboxRepository).save(argThat(message ->
                message.getMessageKey().equals("product-1")));
    }

    @Test
    void deleteWritesDeletedEventWithProductSnapshot() {
        when(productRepository.findById("product-1")).thenReturn(Optional.of(product));

        productService.delete("product-1");

        verify(productRepository).delete(product);
        ArgumentCaptor<ProductOutboxMessage> outboxMessage = ArgumentCaptor.forClass(ProductOutboxMessage.class);
        verify(outboxRepository).save(outboxMessage.capture());
        assertEquals("product-1", outboxMessage.getValue().getMessageKey());
    }

    @Test
    void searchUsesPagedRepositoryQuery() {
        when(productRepository.findByNameContainingIgnoreCase(eq("running"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product)));

        ProductPageResponse<ProductResponse> result = productService.search("running", 0, 10);

        assertEquals("Running Shoes", result.content().getFirst().name());
        verify(productRepository).findByNameContainingIgnoreCase(eq("running"), any(Pageable.class));
    }

    private Product product() {
        Product value = new Product();
        value.setId("product-1");
        value.setName("Running Shoes");
        value.setDescription("Lightweight running shoes");
        value.setCategory("footwear");
        value.setBrand("Acme");
        value.setPrice(new BigDecimal("2999.00"));
        value.setAttributes(Map.of("color", "black", "size", "10"));
        value.setCreatedAt(java.time.Instant.parse("2026-01-01T00:00:00Z"));
        value.setUpdatedAt(value.getCreatedAt());
        return value;
    }

    private ProductRequest request() {
        return new ProductRequest(
                "Running Shoes",
                "Lightweight running shoes",
                "footwear",
                "Acme",
                new BigDecimal("2999.00"),
                Map.of("color", "black", "size", "10")
        );
    }
}
