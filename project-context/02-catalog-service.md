# 02-catalog-service


---

## File: `services/catalog-service/build.gradle`

```groovy
plugins {
	id 'java'
	id 'org.springframework.boot' version '4.1.1'
	id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.ecommerce'
version = '0.0.1-SNAPSHOT'

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	testImplementation 'org.springframework.boot:spring-boot-starter-test'
	implementation 'org.springframework.boot:spring-boot-starter-actuator'
	implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
	implementation 'org.springframework.boot:spring-boot-starter-flyway'
	implementation 'org.springframework.boot:spring-boot-starter-kafka'
	implementation 'org.springframework.boot:spring-boot-starter-security'
	implementation 'org.springframework.boot:spring-boot-starter-validation'
	implementation 'org.springframework.boot:spring-boot-starter-webmvc'
	implementation 'org.flywaydb:flyway-database-postgresql'
	implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
	runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
	runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'
	runtimeOnly 'org.postgresql:postgresql'
	testImplementation 'org.springframework.boot:spring-boot-starter-actuator-test'
	testImplementation 'org.springframework.boot:spring-boot-starter-security-test'
	testImplementation 'org.springframework.boot:spring-boot-starter-data-jpa-test'
	testImplementation 'org.springframework.boot:spring-boot-starter-validation-test'
	testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
	testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
	testRuntimeOnly 'com.h2database:h2'
}

tasks.named('test') {
	useJUnitPlatform()
}

```

---

## File: `services/catalog-service/gradle/wrapper/gradle-wrapper.properties`

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-9.7.1-bin.zip
networkTimeout=120000
retries=0
retryBackOffMs=500
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists

```

---

## File: `services/catalog-service/HELP.md`

```markdown
# Getting Started

### Reference Documentation
For further reference, please consider the following sections:

* [Official Gradle documentation](https://docs.gradle.org)
* [Spring Boot Gradle Plugin Reference Guide](https://docs.spring.io/spring-boot/4.1.1/gradle-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/4.1.1/gradle-plugin/packaging-oci-image.html)
* [Spring Web](https://docs.spring.io/spring-boot/4.1.1/reference/web/servlet.html)
* [Spring Data MongoDB](https://docs.spring.io/spring-boot/4.1.1/reference/data/nosql.html#data.nosql.mongodb)
* [Validation](https://docs.spring.io/spring-boot/4.1.1/reference/io/validation.html)
* [Spring Boot Actuator](https://docs.spring.io/spring-boot/4.1.1/reference/actuator/index.html)

### Guides
The following guides illustrate how to use some features concretely:

* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)
* [Accessing Data with MongoDB](https://spring.io/guides/gs/accessing-data-mongodb/)
* [Validation](https://spring.io/guides/gs/validating-form-input/)
* [Building a RESTful Web Service with Spring Boot Actuator](https://spring.io/guides/gs/actuator-service/)

### Additional Links
These additional references should also help you:

* [Gradle Build Scans – insights for your project's build](https://scans.gradle.com#gradle)


```

---

## File: `services/catalog-service/README.md`

```markdown
# Catalog Service

Catalog Service owns product details and prices. Inventory Service owns stock quantities; catalog responses intentionally do not include stock.

## Product API

- `GET /api/products?page=0&size=20` lists products. `size` is limited to 100.
- `GET /api/products?search=running&page=0&size=20` searches product names.
- `GET /api/products/{id}` returns one product.
- `POST /api/products`, `PUT /api/products/{id}`, and `DELETE /api/products/{id}` require an `ADMIN` bearer token.

Create and update accept product fields only. The service generates IDs and timestamps. List and search responses use a page envelope containing `content`, `page`, `size`, `totalElements`, and `totalPages`.

## Product events

Products and their attributes are stored in PostgreSQL. Create, update, and delete operations add `ProductCreated`, `ProductUpdated`, or `ProductDeleted` records to the `product_outbox` table in the same PostgreSQL transaction as the product change. A scheduled publisher sends those records to the `catalog-events` Kafka topic with the product ID as the message key. Catalog Service declares the topic with three partitions and one replica for the local single-broker setup.

Delivery is at least once: a process restart after Kafka accepts a message but before PostgreSQL marks it published can produce a duplicate. Events include an `eventId`; consumers should deduplicate on that ID. Flyway creates the catalog tables when the service starts.

The event contains a product snapshot for indexing and downstream projections. Inventory remains a separate source of stock data.

```

---

## File: `services/catalog-service/settings.gradle`

```groovy
rootProject.name = 'catalog-service'

```

---

## File: `services/catalog-service/src/main/java/com/ecommerce/catalog/CatalogServiceApplication.java`

```java
package com.ecommerce.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CatalogServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(CatalogServiceApplication.class, args);
	}

}

```

---

## File: `services/catalog-service/src/main/java/com/ecommerce/catalog/config/KafkaTopicConfiguration.java`

```java
package com.ecommerce.catalog.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfiguration {

    @Bean
    @ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
    NewTopic catalogEventsTopic(
            @Value("${app.kafka.product-topic:catalog-events}") String productTopic
    ) {
        return TopicBuilder.name(productTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}

```

---

## File: `services/catalog-service/src/main/java/com/ecommerce/catalog/events/ProductEvent.java`

```java
package com.ecommerce.catalog.events;

import com.ecommerce.catalog.product.Product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ProductEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String productId,
        ProductSnapshot product
) {
    public static ProductEvent from(String type, Product product) {
        return new ProductEvent(
                UUID.randomUUID().toString(),
                type,
                Instant.now(),
                product.getId(),
                new ProductSnapshot(
                        product.getName(),
                        product.getDescription(),
                        product.getCategory(),
                        product.getBrand(),
                        product.getPrice(),
                        product.getAttributes()
                )
        );
    }

    public record ProductSnapshot(
            String name,
            String description,
            String category,
            String brand,
            BigDecimal price,
            Map<String, String> attributes
    ) {
    }
}

```

---

## File: `services/catalog-service/src/main/java/com/ecommerce/catalog/events/ProductOutboxMessage.java`

```java
package com.ecommerce.catalog.events;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "product_outbox", indexes = {
        @Index(name = "idx_product_outbox_pending", columnList = "published_at, created_at, id")
})
public class ProductOutboxMessage {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(nullable = false, length = 200)
    private String topic;

    @Column(name = "message_key", nullable = false, length = 200)
    private String messageKey;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    protected ProductOutboxMessage() {
    }

    public ProductOutboxMessage(String id, String topic, String messageKey, String payload) {
        this.id = id;
        this.topic = topic;
        this.messageKey = messageKey;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getTopic() { return topic; }
    public String getMessageKey() { return messageKey; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public int getAttempts() { return attempts; }
    public String getLastError() { return lastError; }

    public void markPublished() {
        publishedAt = Instant.now();
        lastError = null;
    }

    public void markAttemptFailed(String error) {
        attempts++;
        lastError = error.length() > 2000 ? error.substring(0, 2000) : error;
    }
}

```

---

## File: `services/catalog-service/src/main/java/com/ecommerce/catalog/events/ProductOutboxPublisher.java`

```java
package com.ecommerce.catalog.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class ProductOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(ProductOutboxPublisher.class);

    private final ProductOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final boolean kafkaEnabled;

    public ProductOutboxPublisher(
            ProductOutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${app.kafka.enabled:true}") boolean kafkaEnabled
    ) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaEnabled = kafkaEnabled;
    }

    @Scheduled(fixedDelayString = "${app.kafka.outbox-poll-interval:1000}")
    public void publishPending() {
        if (!kafkaEnabled) {
            return;
        }

        for (ProductOutboxMessage message : outboxRepository
                .findTop50ByPublishedAtIsNullOrderByCreatedAtAscIdAsc()) {
            try {
                kafkaTemplate.send(message.getTopic(), message.getMessageKey(), message.getPayload())
                        .get(10, TimeUnit.SECONDS);
                message.markPublished();
                outboxRepository.save(message);
            } catch (Exception exception) {
                String messageText = exception.getMessage() == null
                        ? exception.getClass().getSimpleName()
                        : exception.getMessage();
                message.markAttemptFailed(messageText);
                outboxRepository.save(message);
                log.warn("Could not publish product outbox message {}", message.getId(), exception);
            }
        }
    }
}

```

---

## File: `services/catalog-service/src/main/java/com/ecommerce/catalog/events/ProductOutboxRepository.java`

```java
package com.ecommerce.catalog.events;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductOutboxRepository extends JpaRepository<ProductOutboxMessage, String> {
    List<ProductOutboxMessage> findTop50ByPublishedAtIsNullOrderByCreatedAtAscIdAsc();
}

```

---

## File: `services/catalog-service/src/main/java/com/ecommerce/catalog/product/ProductController.java`

```java
package com.ecommerce.catalog.product;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return productService.create(request);
    }

    @GetMapping
    public ProductPageResponse<ProductResponse> findAll(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (search != null && !search.isBlank()) {
            if (search.length() > 200) {
                throw new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "search must be at most 200 characters");
            }
            return productService.search(search.trim(), page, size);
        }
        return productService.findAll(page, size);
    }

    @GetMapping("/{id}")
    public ProductResponse findById(@PathVariable String id) {
        return productService.findById(id);
    }

    @PutMapping("/{id}")
    public ProductResponse update(
            @PathVariable String id,
            @Valid @RequestBody ProductRequest request
    ) {
        return productService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        productService.delete(id);
    }
}

```

---

## File: `services/catalog-service/src/main/java/com/ecommerce/catalog/product/Product.java`

```java
package com.ecommerce.catalog.product;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @NotBlank
    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 5000)
    private String description;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String category;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String brand;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @ElementCollection
    @CollectionTable(name = "product_attributes", joinColumns = @JoinColumn(name = "product_id"))
    @MapKeyColumn(name = "attribute_name", length = 100)
    @Column(name = "attribute_value", length = 500)
    private Map<String, String> attributes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Product() {
    }

    public Product(
            String id,
            String name,
            String description,
            String category,
            String brand,
            BigDecimal price,
            Map<String, String> attributes,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.brand = brand;
        this.price = price;
        this.attributes = attributes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

```

---

## File: `services/catalog-service/src/main/java/com/ecommerce/catalog/product/ProductPageResponse.java`

```java
package com.ecommerce.catalog.product;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record ProductPageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <S, T> ProductPageResponse<T> from(Page<S> source, Function<S, T> mapper) {
        return new ProductPageResponse<>(
                source.getContent().stream().map(mapper).toList(),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages()
        );
    }
}

```

---

## File: `services/catalog-service/src/main/java/com/ecommerce/catalog/product/ProductRepository.java`

```java
package com.ecommerce.catalog.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductRepository extends JpaRepository<Product, String> {

    Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);
}

```

---

## File: `services/catalog-service/src/main/java/com/ecommerce/catalog/product/ProductRequest.java`

```java
package com.ecommerce.catalog.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

public record ProductRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 5000) String description,
        @NotBlank @Size(max = 100) String category,
        @NotBlank @Size(max = 100) String brand,
        @NotNull @DecimalMin(value = "0.0", inclusive = false)
        @Digits(integer = 10, fraction = 2) BigDecimal price,
        @Size(max = 50) Map<@NotBlank @Size(max = 100) String, @Size(max = 500) String> attributes
) {
}

```

---

## File: `services/catalog-service/src/main/java/com/ecommerce/catalog/product/ProductResponse.java`

```java
package com.ecommerce.catalog.product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record ProductResponse(
        String id,
        String name,
        String description,
        String category,
        String brand,
        BigDecimal price,
        Map<String, String> attributes,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getCategory(),
                product.getBrand(),
                product.getPrice(),
                product.getAttributes(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}

```

---

## File: `services/catalog-service/src/main/java/com/ecommerce/catalog/product/ProductService.java`

```java
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

```

---

## File: `services/catalog-service/src/main/java/com/ecommerce/catalog/security/JwtAuthenticationFilter.java`

```java
package com.ecommerce.catalog.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final SecretKey secretKey;

    public JwtAuthenticationFilter(
            @Value("${app.jwt.secret}") String secret
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(authorization.substring(7))
                    .getPayload();

            Object roleClaim = claims.get("roles");
            if (claims.getSubject() == null || claims.getSubject().isBlank()
                    || !(roleClaim instanceof Collection<?> roles)
                    || roles.isEmpty()
                    || roles.stream().anyMatch(role -> !(role instanceof String))) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid bearer token");
                return;
            }

            List<SimpleGrantedAuthority> authorities = roles.stream()
                    .map(String.class::cast)
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (RuntimeException exception) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid bearer token");
        }
    }
}

```

---

## File: `services/catalog-service/src/main/java/com/ecommerce/catalog/security/SecurityConfiguration.java`

```java
package com.ecommerce.catalog.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(
            JwtAuthenticationFilter jwtAuthenticationFilter
    ) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(jwtAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/products").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/products/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/products/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}

```

---

## File: `services/catalog-service/src/main/resources/application.properties`

```properties
spring.application.name=catalog-service
server.port=8080

spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/catalog_db}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME:ecommerce}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:ecommerce_dev_password}
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true

app.jwt.secret=${APP_JWT_SECRET:local-development-secret-change-this-to-a-long-random-value}
app.kafka.enabled=${APP_KAFKA_ENABLED:false}
spring.kafka.bootstrap-servers=${SPRING_KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer
app.kafka.product-topic=${KAFKA_PRODUCT_TOPIC:catalog-events}
app.kafka.outbox-poll-interval=${KAFKA_OUTBOX_POLL_INTERVAL:1000}

management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=always
management.health.kafka.enabled=${APP_KAFKA_ENABLED:false}

spring.jackson.default-property-inclusion=non_null

```

---

## File: `services/catalog-service/src/main/resources/db/migration/V1__create_catalog_tables.sql`

```sql
CREATE TABLE products (
    id           VARCHAR(36) PRIMARY KEY,
    name         VARCHAR(200) NOT NULL,
    description  VARCHAR(5000),
    category     VARCHAR(100) NOT NULL,
    brand        VARCHAR(100) NOT NULL,
    price        NUMERIC(12, 2) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL
);

CREATE TABLE product_attributes (
    product_id      VARCHAR(36) NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    attribute_name  VARCHAR(100) NOT NULL,
    attribute_value VARCHAR(500),
    PRIMARY KEY (product_id, attribute_name)
);

CREATE TABLE product_outbox (
    id           VARCHAR(36) PRIMARY KEY,
    topic        VARCHAR(200) NOT NULL,
    message_key  VARCHAR(200) NOT NULL,
    payload      TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempts     INTEGER NOT NULL DEFAULT 0,
    last_error   VARCHAR(2000)
);

CREATE INDEX idx_product_outbox_pending
    ON product_outbox (published_at, created_at, id);

```

---

## File: `services/catalog-service/src/test/java/com/ecommerce/catalog/CatalogServiceApplicationTests.java`

```java
package com.ecommerce.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CatalogServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}

```

---

## File: `services/catalog-service/src/test/java/com/ecommerce/catalog/product/ProductControllerTest.java`

```java
package com.ecommerce.catalog.product;

import com.ecommerce.catalog.security.JwtAuthenticationFilter;
import com.ecommerce.catalog.security.SecurityConfiguration;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
@Import({SecurityConfiguration.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = "app.jwt.secret=test-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm")
class ProductControllerTest {

    private static final String TEST_SECRET =
            "test-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    void adminCanCreateProductAndServerControlsItsIdentity() throws Exception {
        when(productService.create(any(ProductRequest.class))).thenReturn(response());

        mockMvc.perform(post("/api/products")
                        .header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"attacker-id","name":"Running Shoes","description":"Lightweight",
                                 "category":"footwear","brand":"Acme","price":2999.00,
                                 "stockQuantity":999,"createdAt":"2000-01-01T00:00:00Z"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("server-id"))
                .andExpect(jsonPath("$.createdAt").value("2026-09-28T00:00:00Z"))
                .andExpect(jsonPath("$.stockQuantity").doesNotExist());
        verify(productService).create(any(ProductRequest.class));
    }

    @Test
    void anonymousCannotCreateProduct() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(productService);
    }

    @Test
    void invalidBearerTokenCannotCreateProduct() throws Exception {
        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer not-a-signed-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(productService);
    }

    @Test
    void customerCannotCreateProduct() throws Exception {
        mockMvc.perform(post("/api/products")
                        .header("Authorization", bearer("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(productService);
    }

    @Test
    void productListingIsPublicAndPaged() throws Exception {
        when(productService.findAll(0, 20)).thenReturn(new ProductPageResponse<>(
                List.of(response()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Running Shoes"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void searchPassesPaginationToService() throws Exception {
        when(productService.search("running", 1, 10)).thenReturn(new ProductPageResponse<>(
                List.of(response()), 1, 10, 11, 2));

        mockMvc.perform(get("/api/products")
                        .param("search", "running")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalPages").value(2));

        verify(productService).search("running", 1, 10);
    }

    @Test
    void adminCanDeleteProduct() throws Exception {
        mockMvc.perform(delete("/api/products/product-1")
                        .header("Authorization", bearer("ADMIN")))
                .andExpect(status().isNoContent());
        verify(productService).delete("product-1");
    }

    @Test
    void customerCannotDeleteProduct() throws Exception {
        mockMvc.perform(delete("/api/products/product-1")
                        .header("Authorization", bearer("CUSTOMER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(productService);
    }

    @Test
    void rejectsInvalidPageSize() throws Exception {
        when(productService.findAll(0, 101)).thenThrow(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid size"));

        mockMvc.perform(get("/api/products").param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidProductPrice() throws Exception {
        mockMvc.perform(post("/api/products")
                        .header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson().replace("2999.00", "-1.00")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(productService);
    }

    private String validRequestJson() {
        return """
                {"name":"Running Shoes","description":"Lightweight running shoes",
                 "category":"footwear","brand":"Acme","price":2999.00,
                 "attributes":{"color":"black","size":"10"}}
                """;
    }

    private String bearer(String role) {
        var key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("admin-user")
                .claim("roles", new String[]{role})
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
        return "Bearer " + token;
    }

    private ProductResponse response() {
        return new ProductResponse(
                "server-id", "Running Shoes", "Lightweight running shoes", "footwear", "Acme",
                new BigDecimal("2999.00"), Map.of("color", "black"),
                Instant.parse("2026-09-28T00:00:00Z"), Instant.parse("2026-09-28T00:00:00Z")
        );
    }
}

```

---

## File: `services/catalog-service/src/test/java/com/ecommerce/catalog/product/ProductServiceTest.java`

```java
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

```

---

## File: `services/catalog-service/src/test/resources/application.properties`

```properties
spring.datasource.url=jdbc:h2:mem:catalog;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=create-drop
spring.flyway.enabled=false
app.jwt.secret=catalog-test-secret-that-is-long-enough-for-hmac

```
