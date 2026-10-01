package com.ecommerce.catalog.product;

import com.ecommerce.catalog.events.ProductEvent;
import com.ecommerce.catalog.events.ProductOutboxMessage;
import com.ecommerce.catalog.events.ProductOutboxRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

@Service
public class ProductService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProductRepository productRepository;
    private final ProductOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final String productTopic;
    @Value("${app.kafka.enabled:true}")
    private boolean kafkaEnabled = true;

    public ProductService(
            ProductRepository productRepository,
            ProductOutboxRepository outboxRepository,
            ObjectMapper objectMapper,
            @Value("${app.kafka.product-topic:catalog-events}") String productTopic
    ) {
        this.productRepository = productRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.productTopic = productTopic;
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Instant now = Instant.now();
        Product product = new Product();
        product.setId(UUID.randomUUID().toString());
        applyRequest(product, request);
        product.setCreatedAt(now);
        product.setUpdatedAt(now);

        Product saved = productRepository.save(product);
        enqueue(ProductEvent.from("ProductCreated", saved));
        return ProductResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ProductPageResponse<ProductResponse> findAll(int page, int size) {
        return ProductPageResponse.from(
                productRepository.findAll(pageRequest(page, size)),
                ProductResponse::from
        );
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(String id) {
        return ProductResponse.from(findProduct(id));
    }

    @Transactional
    public ProductResponse update(String id, ProductRequest request) {
        Product product = findProduct(id);
        applyRequest(product, request);
        product.setUpdatedAt(Instant.now());

        Product saved = productRepository.save(product);
        enqueue(ProductEvent.from("ProductUpdated", saved));
        return ProductResponse.from(saved);
    }

    @Transactional
    public void delete(String id) {
        Product product = findProduct(id);
        productRepository.delete(product);
        enqueue(ProductEvent.from("ProductDeleted", product));
    }

    @Transactional(readOnly = true)
    public ProductPageResponse<ProductResponse> search(String query, int page, int size) {
        return ProductPageResponse.from(
                productRepository.findByNameContainingIgnoreCase(query, pageRequest(page, size)),
                ProductResponse::from
        );
    }

    private Product findProduct(String id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    private PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "page must be non-negative and size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private void applyRequest(Product product, ProductRequest request) {
        product.setName(request.name().trim());
        product.setDescription(request.description());
        product.setCategory(request.category().trim());
        product.setBrand(request.brand().trim());
        product.setPrice(request.price());
        product.setAttributes(request.attributes());
    }

    private void enqueue(ProductEvent event) {
        if (!kafkaEnabled) {
            return;
        }

        ProductOutboxMessage message = new ProductOutboxMessage(
                event.eventId(),
                productTopic,
                event.productId(),
                objectMapper.writeValueAsString(event)
        );
        outboxRepository.save(message);
    }
}
