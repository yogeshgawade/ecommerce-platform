# 05-inventory-service


---

## File: `services/inventory-service/build.gradle`

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
	implementation 'org.springframework.boot:spring-boot-starter-actuator'
	implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
	implementation 'org.springframework.boot:spring-boot-starter-flyway'
	implementation 'org.springframework.boot:spring-boot-starter-kafka'
	implementation 'org.springframework.boot:spring-boot-starter-security'
	implementation 'org.springframework.boot:spring-boot-starter-validation'
	implementation 'org.springframework.boot:spring-boot-starter-webmvc'
	implementation 'com.fasterxml.jackson.core:jackson-databind'
	implementation 'com.fasterxml.jackson.datatype:jackson-datatype-jsr310'
	runtimeOnly 'org.postgresql:postgresql'
	implementation 'org.flywaydb:flyway-database-postgresql'
	implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
	runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
	runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'
	testImplementation 'org.springframework.boot:spring-boot-starter-actuator-test'
	testImplementation 'org.springframework.boot:spring-boot-starter-data-jpa-test'
	testImplementation 'org.springframework.boot:spring-boot-starter-kafka-test'
	testImplementation 'org.springframework.boot:spring-boot-starter-validation-test'
	testImplementation 'org.springframework.boot:spring-boot-starter-security-test'
	testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
	testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
	testRuntimeOnly 'com.h2database:h2'
}

tasks.named('test') {
	useJUnitPlatform()
}

```

---

## File: `services/inventory-service/gradle/wrapper/gradle-wrapper.properties`

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-9.7.1-bin.zip
networkTimeout=10000
retries=0
retryBackOffMs=500
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists

```

---

## File: `services/inventory-service/HELP.md`

```markdown
# Getting Started

### Reference Documentation
For further reference, please consider the following sections:

* [Official Gradle documentation](https://docs.gradle.org)
* [Spring Boot Gradle Plugin Reference Guide](https://docs.spring.io/spring-boot/4.1.1/gradle-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/4.1.1/gradle-plugin/packaging-oci-image.html)
* [Spring Web](https://docs.spring.io/spring-boot/4.1.1/reference/web/servlet.html)
* [Spring Boot Actuator](https://docs.spring.io/spring-boot/4.1.1/reference/actuator/index.html)
* [Spring Data JPA](https://docs.spring.io/spring-boot/4.1.1/reference/data/sql.html#data.sql.jpa-and-spring-data)
* [Validation](https://docs.spring.io/spring-boot/4.1.1/reference/io/validation.html)
* [Spring for Apache Kafka](https://docs.spring.io/spring-boot/4.1.1/reference/messaging/kafka.html)

### Guides
The following guides illustrate how to use some features concretely:

* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)
* [Building a RESTful Web Service with Spring Boot Actuator](https://spring.io/guides/gs/actuator-service/)
* [Accessing Data with JPA](https://spring.io/guides/gs/accessing-data-jpa/)
* [Validation](https://spring.io/guides/gs/validating-form-input/)

### Additional Links
These additional references should also help you:

* [Gradle Build Scans – insights for your project's build](https://scans.gradle.com#gradle)


```

---

## File: `services/inventory-service/README.md`

```markdown
# Inventory Service

Spring Boot service that owns PostgreSQL stock levels and participates in the order saga. It reserves stock when an order is created, releases it when an order is cancelled, and deducts it when an order is confirmed.

## Local setup

From the repository root, create `.env` from `.env.example`, then run:

```bash
docker compose --env-file .env -f infra/docker-compose.yml up -d --build inventory-service
```

Flyway initializes the inventory schema when the service connects to PostgreSQL. `spring.jpa.hibernate.ddl-auto=validate` checks that the migration and entity mappings agree.

## REST API

- `GET /api/inventory?page=0&size=20` lists stock records in product ID order. Page size is limited to 100.
- `GET /api/inventory/{productId}` returns stock and available quantity.
- `PUT /api/inventory/{productId}` accepts `{"quantity":10}` to create or set total stock. The quantity cannot be reduced below currently reserved stock.
- `POST /api/inventory/{productId}/reserve?quantity=N` reserves stock synchronously.
- `POST /api/inventory/{productId}/release?quantity=N` releases a reservation.
- `POST /api/inventory/{productId}/deduct?quantity=N` deducts an existing reservation.
- `GET /actuator/health` reports service health.

All stock mutations are transactional and lock inventory rows to prevent overselling. Reservation changes made for Kafka orders are idempotent by order ID.

REST reads require a valid JWT with `CUSTOMER` or `ADMIN` role; stock mutations require `ADMIN`. Tokens use the same HS256 signing secret and `roles` claim as auth-service. Actuator health/info are public for local infrastructure checks.

## Kafka contract

The service consumes JSON strings from `order-events`. The initial contract is:

```json
{
  "type": "OrderCreated",
  "eventId": "event-uuid",
  "orderId": "order-uuid",
  "items": [
    { "productId": "product-uuid", "quantity": 2 }
  ]
}
```

`OrderCancelled` releases an existing reservation and `OrderConfirmed` commits it. Each event needs `type` and `orderId`; `OrderCreated` also requires a non-empty `items` array. Repeated `OrderCreated` messages for an already-seen order do not reserve twice.

Reservation success and failure events are written to the PostgreSQL outbox in the same transaction as stock changes, then published to `inventory-events`:

```json
{
  "type": "InventoryReserved",
  "eventId": "event-uuid",
  "occurredAt": "2026-09-28T12:00:00Z",
  "orderId": "order-uuid",
  "items": [{ "productId": "product-uuid", "quantity": 2 }],
  "reason": null
}
```

Failure uses `InventoryReservationFailed` and includes a human-readable `reason`. The outbox retries unpublished events after restart or Kafka outages using capped exponential backoff, so a repeatedly failing old message cannot permanently block later messages. Consumers should still be idempotent because delivery is at least once.

## Configuration

Environment variables include `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`, `APP_JWT_SECRET`, `KAFKA_ORDER_TOPIC`, `KAFKA_INVENTORY_TOPIC`, and `KAFKA_OUTBOX_POLL_INTERVAL`. Docker Compose supplies the database credentials, JWT signing secret, and internal Kafka address.

```

---

## File: `services/inventory-service/settings.gradle`

```groovy
rootProject.name = 'inventory-service'

```

---

## File: `services/inventory-service/src/main/java/com/ecommerce/inventory/config/JsonConfiguration.java`

```java
package com.ecommerce.inventory.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JsonConfiguration {

    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder().findAndAddModules().build();
    }
}
```

---

## File: `services/inventory-service/src/main/java/com/ecommerce/inventory/controller/InventoryController.java`

```java
package com.ecommerce.inventory.controller;

import com.ecommerce.inventory.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

@RestController
@RequestMapping("/api/inventory")
@Validated
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public InventoryPageResponse findAll(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("productId").ascending());
        Page<InventoryResponse> response = inventoryService.findAll(pageable)
                .map(InventoryResponse::from);
        return InventoryPageResponse.from(response);
    }

    @GetMapping("/{productId}")
    public InventoryResponse findByProductId(@PathVariable String productId) {
        return InventoryResponse.from(inventoryService.findByProductId(productId));
    }

    @PutMapping("/{productId}")
    public ResponseEntity<InventoryResponse> createOrUpdate(
            @PathVariable String productId,
            @Valid @RequestBody InventoryQuantityRequest request
    ) {
        return ResponseEntity.ok(InventoryResponse.from(
                inventoryService.createOrUpdate(productId, request.quantity())));
    }

    @PostMapping("/{productId}/reserve")
    public ResponseEntity<Boolean> reserveStock(
            @PathVariable String productId,
            @RequestParam @Positive Integer quantity
    ) {
        boolean success = inventoryService.reserveStock(productId, quantity);
        return ResponseEntity.ok(success);
    }

    @PostMapping("/{productId}/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void releaseReservedStock(
            @PathVariable String productId,
            @RequestParam @Positive Integer quantity
    ) {
        inventoryService.releaseReservedStock(productId, quantity);
    }

    @PostMapping("/{productId}/deduct")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deductStock(
            @PathVariable String productId,
            @RequestParam @Positive Integer quantity
    ) {
        inventoryService.deductStock(productId, quantity);
    }
}

```

---

## File: `services/inventory-service/src/main/java/com/ecommerce/inventory/controller/InventoryPageResponse.java`

```java
package com.ecommerce.inventory.controller;

import org.springframework.data.domain.Page;

import java.util.List;

public record InventoryPageResponse(
        List<InventoryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static InventoryPageResponse from(Page<InventoryResponse> result) {
        return new InventoryPageResponse(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }
}

```

---

## File: `services/inventory-service/src/main/java/com/ecommerce/inventory/controller/InventoryQuantityRequest.java`

```java
package com.ecommerce.inventory.controller;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record InventoryQuantityRequest(
        @NotNull @Min(0) Integer quantity
) {
}

```

---

## File: `services/inventory-service/src/main/java/com/ecommerce/inventory/controller/InventoryResponse.java`

```java
package com.ecommerce.inventory.controller;

import com.ecommerce.inventory.model.Inventory;

import java.time.Instant;

public record InventoryResponse(
        String productId,
        int quantity,
        int reservedQuantity,
        int availableQuantity,
        Instant lastUpdatedAt
) {
    public static InventoryResponse from(Inventory inventory) {
        return new InventoryResponse(
                inventory.getProductId(),
                inventory.getQuantity(),
                inventory.getReservedQuantity(),
                inventory.getAvailableQuantity(),
                inventory.getLastUpdatedAt()
        );
    }
}

```

---

## File: `services/inventory-service/src/main/java/com/ecommerce/inventory/InventoryApplication.java`

```java
package com.ecommerce.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class InventoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryApplication.class, args);
    }
}

```

---

## File: `services/inventory-service/src/main/java/com/ecommerce/inventory/messaging/InventoryEvent.java`

```java
package com.ecommerce.inventory.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InventoryEvent(
        String type,
        String eventId,
        Instant occurredAt,
        String orderId,
        List<OrderEventItem> items,
        String reason
) {
    public InventoryEvent(String type, String orderId, List<OrderEventItem> items, String reason) {
        this(type, UUID.randomUUID().toString(), Instant.now(), orderId, items, reason);
    }
}
```

---

## File: `services/inventory-service/src/main/java/com/ecommerce/inventory/messaging/InventoryOutboxPublisher.java`

```java
package com.ecommerce.inventory.messaging;

import com.ecommerce.inventory.model.InventoryOutboxMessage;
import com.ecommerce.inventory.repository.InventoryOutboxRepository;
import org.springframework.data.domain.PageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
public class InventoryOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(InventoryOutboxPublisher.class);

    private final InventoryOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public InventoryOutboxPublisher(
            InventoryOutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${app.kafka.outbox-poll-interval:1000}")
    public void publishPending() {
        List<InventoryOutboxMessage> pending = outboxRepository
                .findReadyToPublish(Instant.now(), PageRequest.of(0, 25));
        for (InventoryOutboxMessage message : pending) {
            try {
                kafkaTemplate.send(message.getTopic(), message.getMessageKey(), message.getPayload())
                        .get(10, TimeUnit.SECONDS);
                message.markPublished();
                outboxRepository.save(message);
            } catch (Exception exception) {
                message.markAttemptFailed(exception.getMessage() == null
                        ? exception.getClass().getSimpleName()
                        : exception.getMessage());
                outboxRepository.save(message);
                log.warn("Could not publish inventory outbox message {}", message.getId(), exception);
            }
        }
    }
}

```

---

## File: `services/inventory-service/src/main/java/com/ecommerce/inventory/messaging/OrderEventItem.java`

```java
package com.ecommerce.inventory.messaging;

public record OrderEventItem(String productId, Integer quantity) {
}
```

---

## File: `services/inventory-service/src/main/java/com/ecommerce/inventory/messaging/OrderEvent.java`

```java
package com.ecommerce.inventory.messaging;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderEvent(
        @JsonAlias("eventType") String type,
        String eventId,
        String orderId,
        List<OrderEventItem> items
) {
}

```

---

## File: `services/inventory-service/src/main/java/com/ecommerce/inventory/messaging/OrderEventListener.java`

```java
package com.ecommerce.inventory.messaging;

import com.ecommerce.inventory.service.ReservationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final ObjectMapper objectMapper;
    private final ReservationService reservationService;

    public OrderEventListener(ObjectMapper objectMapper, ReservationService reservationService) {
        this.objectMapper = objectMapper;
        this.reservationService = reservationService;
    }

    @KafkaListener(
            topics = "${app.kafka.order-topic:order-events}",
            groupId = "${spring.kafka.consumer.group-id:inventory-service-group}"
    )
    public void consume(String payload) {
        final OrderEvent event;
        try {
            event = objectMapper.readValue(payload, OrderEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid order event JSON", exception);
        }

        if (event.type() == null || event.orderId() == null || event.orderId().isBlank()) {
            throw new IllegalArgumentException("Order event requires type and orderId");
        }

        switch (event.type()) {
            case "OrderCreated" -> reservationService.reserveForOrder(event);
            case "OrderCancelled" -> reservationService.releaseForOrder(event.orderId());
            case "OrderConfirmed" -> reservationService.commitForOrder(event.orderId());
            default -> log.debug("Ignoring unsupported order event type {}", event.type());
        }
    }
}
```

---

## File: `services/inventory-service/src/main/java/com/ecommerce/inventory/model/Inventory.java`

```java
package com.ecommerce.inventory.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, unique = true)
    private String productId;

    @NotNull
    @Min(0)
    @Column(nullable = false)
    private Integer quantity;

    @NotNull
    @Min(0)
    @Column(nullable = false)
    private Integer reservedQuantity = 0;

    @NotNull
    @Column(nullable = false)
    private Instant lastUpdatedAt;

    @Version
    private Long version;

    public Inventory() {
    }

    public Inventory(String productId, Integer quantity) {
        this.productId = productId;
        this.quantity = quantity;
        this.reservedQuantity = 0;
        this.lastUpdatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Integer getReservedQuantity() { return reservedQuantity; }
    public void setReservedQuantity(Integer reservedQuantity) { this.reservedQuantity = reservedQuantity; }

    public Instant getLastUpdatedAt() { return lastUpdatedAt; }
    public void setLastUpdatedAt(Instant lastUpdatedAt) { this.lastUpdatedAt = lastUpdatedAt; }

    public Integer getAvailableQuantity() {
        return quantity - reservedQuantity;
    }

    public Long getVersion() { return version; }
}

```

---

## File: `services/inventory-service/src/main/java/com/ecommerce/inventory/model/InventoryOutboxMessage.java`

```java
package com.ecommerce.inventory.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "inventory_outbox")
public class InventoryOutboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String topic;

    @Column(name = "message_key", nullable = false)
    private String messageKey;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private Integer attempts;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    protected InventoryOutboxMessage() {
    }

    public InventoryOutboxMessage(String topic, String messageKey, String payload) {
        this.topic = topic;
        this.messageKey = messageKey;
        this.payload = payload;
        this.createdAt = Instant.now();
        this.attempts = 0;
    }

    public Long getId() { return id; }
    public String getTopic() { return topic; }
    public String getMessageKey() { return messageKey; }
    public String getPayload() { return payload; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getLastError() { return lastError; }
    public Integer getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }

    public void markPublished() {
        this.publishedAt = Instant.now();
        this.nextAttemptAt = null;
        this.lastError = null;
    }

    public void markAttemptFailed(String error) {
        this.attempts++;
        this.lastError = error.length() > 2000 ? error.substring(0, 2000) : error;
        long retryDelayMillis = Math.min(300_000L, 1_000L << Math.min(attempts - 1, 9));
        this.nextAttemptAt = Instant.now().plusMillis(retryDelayMillis);
    }
}

```

---

## File: `services/inventory-service/src/main/java/com/ecommerce/inventory/model/InventoryReservation.java`

```java
package com.ecommerce.inventory.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "inventory_reservation")
public class InventoryReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ReservationStatus status;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InventoryReservation() {
    }

    public InventoryReservation(String orderId, ReservationStatus status, String failureReason) {
        this.orderId = orderId;
        this.status = status;
        this.failureReason = failureReason;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getOrderId() { return orderId; }
    public ReservationStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }

    public void transitionTo(ReservationStatus status, String failureReason) {
        this.status = status;
        this.failureReason = failureReason;
        this.updatedAt = Instant.now();
    }
}
```

---

## File: `services/inventory-service/src/main/java/com/ecommerce/inventory/model/InventoryReservationLine.java`

```java
package com.ecommerce.inventory.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "inventory_reservation_line", uniqueConstraints =
        @UniqueConstraint(name = "inventory_reservation_line_order_product_unique", columnNames = {"order_id", "product_id"}))
public class InventoryReservationLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(nullable = false)
    private Integer quantity;

    protected InventoryReservationLine() {
    }

    public InventoryReservationLine(String orderId, String productId, Integer quantity) {
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
    }

    public String getProductId() { return productId; }
    public Integer getQuantity() { return quantity; }
}
```

---

## File: `services/inventory-service/src/main/java/com/ecommerce/inventory/model/ReservationStatus.java`

```java
package com.ecommerce.inventory.model;

public enum ReservationStatus {
    RESERVED,
    REJECTED,
    RELEASED,
    COMMITTED
}
```

---

## File: `services/inventory-service/src/main/java/com/ecommerce/inventory/repository/InventoryOutboxRepository.java`

```java
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

```

---

## File: `services/inventory-service/src/main/java/com/ecommerce/inventory/repository/InventoryRepository.java`

```java
package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.model.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findByProductId(String productId);
    boolean existsByProductId(String productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select inventory from Inventory inventory where inventory.productId = :productId")
    Optional<Inventory> findByProductIdForUpdate(@Param("productId") String productId);
}

```

---

## File: `services/inventory-service/src/main/java/com/ecommerce/inventory/repository/InventoryReservationLineRepository.java`

```java
package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.model.InventoryReservationLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryReservationLineRepository extends JpaRepository<InventoryReservationLine, Long> {
    List<InventoryReservationLine> findAllByOrderIdOrderByProductIdAsc(String orderId);
}
```

---

## File: `services/inventory-service/src/main/java/com/ecommerce/inventory/repository/InventoryReservationRepository.java`

```java
package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.model.InventoryReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {
    Optional<InventoryReservation> findByOrderId(String orderId);
    boolean existsByOrderId(String orderId);
}
```

---

## File: `services/inventory-service/src/main/java/com/ecommerce/inventory/security/JwtAuthenticationFilter.java`

```java
package com.ecommerce.inventory.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Set;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_ROLES = Set.of("CUSTOMER", "ADMIN");

    private final SecretKey signingKey;

    public JwtAuthenticationFilter(@Value("${app.jwt.secret}") String secret) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret must contain at least 32 UTF-8 bytes");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.length() <= 7
                || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Bearer token required");
            return;
        }

        final Claims claims;
        final List<SimpleGrantedAuthority> authorities;
        try {
            claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(authorization.substring(7).trim())
                    .getPayload();
            Object rawRoles = claims.get("roles");
            if (!(rawRoles instanceof Collection<?> roles)
                    || claims.getSubject() == null || claims.getSubject().isBlank()
                    || roles.isEmpty()
                    || roles.stream().anyMatch(role -> !(role instanceof String value)
                            || !ALLOWED_ROLES.contains(value))) {
                SecurityContextHolder.clearContext();
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token claims");
                return;
            }

            authorities = roles.stream()
                    .map(String.class::cast)
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
            if (authorities.isEmpty()) {
                SecurityContextHolder.clearContext();
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token has no valid roles");
                return;
            }

        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid bearer token");
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities));
        filterChain.doFilter(request, response);
    }
}

```

---

## File: `services/inventory-service/src/main/java/com/ecommerce/inventory/security/SecurityConfiguration.java`

```java
package com.ecommerce.inventory.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/inventory/**").hasAnyRole("CUSTOMER", "ADMIN")
                        .requestMatchers("/api/inventory/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
```

---

## File: `services/inventory-service/src/main/java/com/ecommerce/inventory/service/InventoryService.java`

```java
package com.ecommerce.inventory.service;

import com.ecommerce.inventory.model.Inventory;
import com.ecommerce.inventory.repository.InventoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    public Page<Inventory> findAll(Pageable pageable) {
        return inventoryRepository.findAll(pageable);
    }

    public Inventory findByProductId(String productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Inventory not found for product: " + productId
                ));
    }

    @Transactional
    public Inventory createOrUpdate(String productId, Integer quantity) {
        requireProductId(productId);
        requireNonNegative(quantity);

        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseGet(() -> new Inventory(productId, quantity));
        if (quantity < inventory.getReservedQuantity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Stock cannot be reduced below the reserved quantity");
        }

        inventory.setQuantity(quantity);
        inventory.setLastUpdatedAt(Instant.now());
        return inventoryRepository.save(inventory);
    }

    @Transactional
    public boolean reserveStock(String productId, Integer quantity) {
        requirePositive(quantity);
        Inventory inventory = findForUpdate(productId);

        if (inventory.getAvailableQuantity() < quantity) {
            return false;
        }

        inventory.setReservedQuantity(inventory.getReservedQuantity() + quantity);
        inventory.setLastUpdatedAt(Instant.now());
        inventoryRepository.save(inventory);
        return true;
    }

    @Transactional
    public void releaseReservedStock(String productId, Integer quantity) {
        requirePositive(quantity);
        Inventory inventory = findForUpdate(productId);

        if (inventory.getReservedQuantity() < quantity) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot release more stock than is currently reserved");
        }

        inventory.setReservedQuantity(inventory.getReservedQuantity() - quantity);
        inventory.setLastUpdatedAt(Instant.now());
        inventoryRepository.save(inventory);
    }

    @Transactional
    public void deductStock(String productId, Integer quantity) {
        requirePositive(quantity);
        Inventory inventory = findForUpdate(productId);

        if (inventory.getReservedQuantity() < quantity) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot deduct more than reserved quantity"
            );
        }

        inventory.setQuantity(inventory.getQuantity() - quantity);
        inventory.setReservedQuantity(inventory.getReservedQuantity() - quantity);
        inventory.setLastUpdatedAt(Instant.now());
        inventoryRepository.save(inventory);
    }

    private Inventory findForUpdate(String productId) {
        requireProductId(productId);
        return inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Inventory not found for product: " + productId));
    }

    private void requireProductId(String productId) {
        if (productId == null || productId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "productId is required");
        }
    }

    private void requirePositive(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must be greater than zero");
        }
    }

    private void requireNonNegative(Integer quantity) {
        if (quantity == null || quantity < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must not be negative");
        }
    }
}

```

---

## File: `services/inventory-service/src/main/java/com/ecommerce/inventory/service/ReservationService.java`

```java
package com.ecommerce.inventory.service;

import com.ecommerce.inventory.messaging.InventoryEvent;
import com.ecommerce.inventory.messaging.OrderEvent;
import com.ecommerce.inventory.messaging.OrderEventItem;
import com.ecommerce.inventory.model.Inventory;
import com.ecommerce.inventory.model.InventoryOutboxMessage;
import com.ecommerce.inventory.model.InventoryReservation;
import com.ecommerce.inventory.model.InventoryReservationLine;
import com.ecommerce.inventory.model.ReservationStatus;
import com.ecommerce.inventory.repository.InventoryOutboxRepository;
import com.ecommerce.inventory.repository.InventoryRepository;
import com.ecommerce.inventory.repository.InventoryReservationLineRepository;
import com.ecommerce.inventory.repository.InventoryReservationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class ReservationService {

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;
    private final InventoryReservationLineRepository reservationLineRepository;
    private final InventoryOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final String inventoryTopic;

    public ReservationService(
            InventoryRepository inventoryRepository,
            InventoryReservationRepository reservationRepository,
            InventoryReservationLineRepository reservationLineRepository,
            InventoryOutboxRepository outboxRepository,
            ObjectMapper objectMapper,
            @Value("${app.kafka.inventory-topic:inventory-events}") String inventoryTopic
    ) {
        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
        this.reservationLineRepository = reservationLineRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.inventoryTopic = inventoryTopic;
    }

    @Transactional
    public void reserveForOrder(OrderEvent event) {
        requireOrderId(event.orderId());
        if (reservationRepository.existsByOrderId(event.orderId())) {
            return;
        }

        TreeMap<String, Integer> quantities = aggregateAndValidate(event.items());
        List<OrderEventItem> normalizedItems = toEventItems(quantities);
        InventoryReservation reservation = reservationRepository.saveAndFlush(
                new InventoryReservation(event.orderId(), ReservationStatus.RESERVED, null));
        List<InventoryReservationLine> reservationLines = quantities.entrySet().stream()
                .map(line -> new InventoryReservationLine(event.orderId(), line.getKey(), line.getValue()))
                .toList();

        Map<String, Inventory> lockedInventory = new TreeMap<>();
        String failureReason = null;
        for (Map.Entry<String, Integer> line : quantities.entrySet()) {
            Inventory inventory = inventoryRepository.findByProductIdForUpdate(line.getKey()).orElse(null);
            if (inventory == null) {
                failureReason = "No inventory exists for product " + line.getKey();
                break;
            }
            if (inventory.getAvailableQuantity() < line.getValue()) {
                failureReason = "Insufficient stock for product " + line.getKey();
                break;
            }
            lockedInventory.put(line.getKey(), inventory);
        }

        reservationLineRepository.saveAll(reservationLines);
        if (failureReason != null) {
            reservation.transitionTo(ReservationStatus.REJECTED, failureReason);
            reservationRepository.save(reservation);
            enqueue(new InventoryEvent("InventoryReservationFailed", event.orderId(), normalizedItems, failureReason));
            return;
        }

        quantities.forEach((productId, quantity) -> {
            Inventory inventory = lockedInventory.get(productId);
            inventory.setReservedQuantity(inventory.getReservedQuantity() + quantity);
        });
        inventoryRepository.saveAll(lockedInventory.values());
        enqueue(new InventoryEvent("InventoryReserved", event.orderId(), normalizedItems, null));
    }

    @Transactional
    public void releaseForOrder(String orderId) {
        InventoryReservation reservation = findReservation(orderId);
        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            return;
        }

        List<InventoryReservationLine> lines = reservationLineRepository.findAllByOrderIdOrderByProductIdAsc(orderId);
        Map<String, Inventory> lockedInventory = lockInventory(lines);
        for (InventoryReservationLine line : lines) {
            Inventory inventory = lockedInventory.get(line.getProductId());
            if (inventory.getReservedQuantity() < line.getQuantity()) {
                throw new IllegalStateException("Reserved quantity is inconsistent for product " + line.getProductId());
            }
            inventory.setReservedQuantity(inventory.getReservedQuantity() - line.getQuantity());
        }
        inventoryRepository.saveAll(lockedInventory.values());
        reservation.transitionTo(ReservationStatus.RELEASED, null);
        reservationRepository.save(reservation);
    }

    @Transactional
    public void commitForOrder(String orderId) {
        InventoryReservation reservation = findReservation(orderId);
        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            return;
        }

        List<InventoryReservationLine> lines = reservationLineRepository.findAllByOrderIdOrderByProductIdAsc(orderId);
        Map<String, Inventory> lockedInventory = lockInventory(lines);
        for (InventoryReservationLine line : lines) {
            Inventory inventory = lockedInventory.get(line.getProductId());
            if (inventory.getReservedQuantity() < line.getQuantity() || inventory.getQuantity() < line.getQuantity()) {
                throw new IllegalStateException("Reserved quantity is inconsistent for product " + line.getProductId());
            }
            inventory.setQuantity(inventory.getQuantity() - line.getQuantity());
            inventory.setReservedQuantity(inventory.getReservedQuantity() - line.getQuantity());
        }
        inventoryRepository.saveAll(lockedInventory.values());
        reservation.transitionTo(ReservationStatus.COMMITTED, null);
        reservationRepository.save(reservation);
    }

    private Map<String, Inventory> lockInventory(List<InventoryReservationLine> lines) {
        Map<String, Inventory> lockedInventory = new TreeMap<>();
        for (InventoryReservationLine line : lines) {
            Inventory inventory = inventoryRepository.findByProductIdForUpdate(line.getProductId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Inventory disappeared for product " + line.getProductId()));
            lockedInventory.put(line.getProductId(), inventory);
        }
        return lockedInventory;
    }

    private InventoryReservation findReservation(String orderId) {
        requireOrderId(orderId);
        return reservationRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No inventory reservation exists for order " + orderId));
    }

    private TreeMap<String, Integer> aggregateAndValidate(List<OrderEventItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("OrderCreated requires at least one item");
        }
        TreeMap<String, Integer> quantities = new TreeMap<>();
        for (OrderEventItem item : items) {
            if (item == null || item.productId() == null || item.productId().isBlank()
                    || item.quantity() == null || item.quantity() <= 0) {
                throw new IllegalArgumentException("Each order item requires productId and a positive quantity");
            }
            quantities.merge(item.productId(), item.quantity(), Math::addExact);
        }
        return quantities;
    }

    private List<OrderEventItem> toEventItems(Map<String, Integer> quantities) {
        List<OrderEventItem> items = new ArrayList<>(quantities.size());
        quantities.forEach((productId, quantity) -> items.add(new OrderEventItem(productId, quantity)));
        return List.copyOf(items);
    }

    private void enqueue(InventoryEvent event) {
        try {
            outboxRepository.save(new InventoryOutboxMessage(
                    inventoryTopic,
                    event.orderId(),
                    objectMapper.writeValueAsString(event)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize inventory event", exception);
        }
    }

    private void requireOrderId(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId is required");
        }
    }
}
```

---

## File: `services/inventory-service/src/main/resources/application.properties`

```properties
spring.application.name=${SPRING_APPLICATION_NAME:inventory-service}
server.port=${SERVER_PORT:8083}

# Database
spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/inventory_db}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME:ecommerce}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:ecommerce_dev_password}
spring.jpa.hibernate.ddl-auto=${SPRING_JPA_HIBERNATE_DDL_AUTO:validate}
spring.jpa.show-sql=${SPRING_JPA_SHOW_SQL:false}
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.flyway.enabled=${SPRING_FLYWAY_ENABLED:true}
app.jwt.secret=${APP_JWT_SECRET:local-development-secret-change-this-to-a-long-random-value}

# Kafka
spring.kafka.bootstrap-servers=${SPRING_KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
spring.kafka.consumer.group-id=${SPRING_KAFKA_CONSUMER_GROUP_ID:inventory-service-group}
spring.kafka.consumer.auto-offset-reset=${SPRING_KAFKA_CONSUMER_AUTO_OFFSET_RESET:earliest}
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer

spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer

# Inventory saga
app.kafka.order-topic=${KAFKA_ORDER_TOPIC:order-events}
app.kafka.inventory-topic=${KAFKA_INVENTORY_TOPIC:inventory-events}
app.kafka.outbox-poll-interval=${KAFKA_OUTBOX_POLL_INTERVAL:1000}

# Actuator
management.endpoints.web.exposure.include=${MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE:health,info}
management.endpoint.health.show-details=${MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS:always}

# Jackson
spring.jackson.default-property-inclusion=non_null

```

---

## File: `services/inventory-service/src/main/resources/db/migration/V1__create_inventory_schema.sql`

```sql
CREATE TABLE inventory (
    id BIGSERIAL PRIMARY KEY,
    product_id VARCHAR(255) NOT NULL UNIQUE,
    quantity INTEGER NOT NULL CHECK (quantity >= 0),
    reserved_quantity INTEGER NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    last_updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT inventory_reserved_within_quantity CHECK (reserved_quantity <= quantity)
);

CREATE TABLE inventory_reservation (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(24) NOT NULL,
    failure_reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE inventory_reservation_line (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(255) NOT NULL REFERENCES inventory_reservation(order_id),
    product_id VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    CONSTRAINT inventory_reservation_line_order_product_unique UNIQUE (order_id, product_id)
);

CREATE INDEX inventory_reservation_line_order_idx ON inventory_reservation_line(order_id);

CREATE TABLE inventory_outbox (
    id BIGSERIAL PRIMARY KEY,
    topic VARCHAR(255) NOT NULL,
    message_key VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(2000)
);

CREATE INDEX inventory_outbox_unpublished_idx ON inventory_outbox(created_at, id)
    WHERE published_at IS NULL;
```

---

## File: `services/inventory-service/src/main/resources/db/migration/V2__add_inventory_outbox_retry_schedule.sql`

```sql
ALTER TABLE inventory_outbox
    ADD COLUMN next_attempt_at TIMESTAMPTZ;

CREATE INDEX inventory_outbox_ready_idx
    ON inventory_outbox(next_attempt_at, created_at, id)
    WHERE published_at IS NULL;

```

---

## File: `services/inventory-service/src/test/java/com/ecommerce/inventory/InventoryApplicationTests.java`

```java
package com.ecommerce.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InventoryApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void healthIsPublicAndInventoryApiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/inventory"))
                .andExpect(status().isUnauthorized());
    }
}

```

---

## File: `services/inventory-service/src/test/java/com/ecommerce/inventory/messaging/OrderEventCompatibilityTest.java`

```java
package com.ecommerce.inventory.messaging;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderEventCompatibilityTest {
    @Test
    void acceptsOrderLifecycleMetadataAddedByOrderService() throws Exception {
        String payload = """
                {"type":"OrderCreated","eventId":"event-1","occurredAt":"2026-09-29T00:00:00Z",
                 "orderId":"order-1","userId":"user-1","totalAmount":12.50,"currency":"USD",
                 "items":[{"productId":"product-1","quantity":1}]}
                """;

        OrderEvent event = JsonMapper.builder().findAndAddModules().build().readValue(payload, OrderEvent.class);

        assertEquals("OrderCreated", event.type());
        assertEquals("order-1", event.orderId());
        assertEquals("product-1", event.items().getFirst().productId());
    }
}

```

---

## File: `services/inventory-service/src/test/java/com/ecommerce/inventory/model/InventoryOutboxMessageTest.java`

```java
package com.ecommerce.inventory.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryOutboxMessageTest {

    @Test
    void failedPublishSchedulesExponentialRetry() {
        InventoryOutboxMessage message = new InventoryOutboxMessage("inventory-events", "order-1", "{}");

        Instant firstFailure = Instant.now();
        message.markAttemptFailed("broker unavailable");
        assertEquals(1, message.getAttempts());
        assertTrue(Duration.between(firstFailure, message.getNextAttemptAt()).toMillis() >= 1000);

        Instant secondFailure = Instant.now();
        message.markAttemptFailed("broker unavailable");
        assertEquals(2, message.getAttempts());
        assertTrue(Duration.between(secondFailure, message.getNextAttemptAt()).toMillis() >= 2000);
    }

    @Test
    void successfulPublishClearsRetryScheduleAndLastError() {
        InventoryOutboxMessage message = new InventoryOutboxMessage("inventory-events", "order-1", "{}");
        message.markAttemptFailed("broker unavailable");

        message.markPublished();

        assertTrue(message.getPublishedAt() != null);
        assertNull(message.getNextAttemptAt());
        assertNull(message.getLastError());
    }
}

```

---

## File: `services/inventory-service/src/test/java/com/ecommerce/inventory/service/InventoryServiceTest.java`

```java
package com.ecommerce.inventory.service;

import com.ecommerce.inventory.model.Inventory;
import com.ecommerce.inventory.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private Inventory inventory;

    @BeforeEach
    void setUp() {
        inventory = new Inventory("product-1", 100);
        inventory.setId(1L);
    }

    @Test
    void findByProductIdShouldReturnInventory() {
        when(inventoryRepository.findByProductId("product-1"))
                .thenReturn(Optional.of(inventory));

        Inventory result = inventoryService.findByProductId("product-1");

        assertEquals("product-1", result.getProductId());
        assertEquals(100, result.getQuantity());
    }

    @Test
    void findByProductIdShouldThrowNotFound() {
        when(inventoryRepository.findByProductId("missing"))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.findByProductId("missing")
        );

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void reserveStockShouldReturnTrueWhenSufficientStock() {
        when(inventoryRepository.findByProductIdForUpdate("product-1"))
                .thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        boolean result = inventoryService.reserveStock("product-1", 10);

        assertTrue(result);
        assertEquals(10, inventory.getReservedQuantity());
        verify(inventoryRepository).save(inventory);
    }

    @Test
    void reserveStockShouldReturnFalseWhenInsufficientStock() {
        when(inventoryRepository.findByProductIdForUpdate("product-1"))
                .thenReturn(Optional.of(inventory));

        boolean result = inventoryService.reserveStock("product-1", 150);

        assertFalse(result);
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void releaseReservedStockShouldDecreaseReservedQuantity() {
        inventory.setReservedQuantity(20);
        when(inventoryRepository.findByProductIdForUpdate("product-1"))
                .thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        inventoryService.releaseReservedStock("product-1", 10);

        assertEquals(10, inventory.getReservedQuantity());
        verify(inventoryRepository).save(inventory);
    }

    @Test
    void deductStockShouldDecreaseQuantityAndReserved() {
        inventory.setReservedQuantity(20);
        when(inventoryRepository.findByProductIdForUpdate("product-1"))
                .thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        inventoryService.deductStock("product-1", 10);

        assertEquals(90, inventory.getQuantity());
        assertEquals(10, inventory.getReservedQuantity());
        verify(inventoryRepository).save(inventory);
    }
}

```

---

## File: `services/inventory-service/src/test/java/com/ecommerce/inventory/service/ReservationServiceTest.java`

```java
package com.ecommerce.inventory.service;

import com.ecommerce.inventory.messaging.OrderEvent;
import com.ecommerce.inventory.messaging.OrderEventItem;
import com.ecommerce.inventory.model.Inventory;
import com.ecommerce.inventory.model.InventoryOutboxMessage;
import com.ecommerce.inventory.model.InventoryReservation;
import com.ecommerce.inventory.model.ReservationStatus;
import com.ecommerce.inventory.repository.InventoryOutboxRepository;
import com.ecommerce.inventory.repository.InventoryRepository;
import com.ecommerce.inventory.repository.InventoryReservationLineRepository;
import com.ecommerce.inventory.repository.InventoryReservationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private InventoryReservationRepository reservationRepository;
    @Mock
    private InventoryReservationLineRepository reservationLineRepository;
    @Mock
    private InventoryOutboxRepository outboxRepository;

    private ReservationService reservationService;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        reservationService = new ReservationService(
                inventoryRepository,
                reservationRepository,
                reservationLineRepository,
                outboxRepository,
                new ObjectMapper().findAndRegisterModules(),
                "inventory-events");
        inventory = new Inventory("product-1", 10);
        inventory.setId(1L);
    }

    @Test
    void reservesAllItemsAndWritesSuccessEventToOutbox() {
        when(reservationRepository.existsByOrderId("order-1")).thenReturn(false);
        when(reservationRepository.saveAndFlush(any(InventoryReservation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryRepository.findByProductIdForUpdate("product-1")).thenReturn(Optional.of(inventory));

        reservationService.reserveForOrder(orderEvent(4));

        assertEquals(4, inventory.getReservedQuantity());
        verify(inventoryRepository).saveAll(any());
        ArgumentCaptor<InventoryOutboxMessage> message = ArgumentCaptor.forClass(InventoryOutboxMessage.class);
        verify(outboxRepository).save(message.capture());
        assertEquals("inventory-events", message.getValue().getTopic());
        assertTrue(message.getValue().getPayload().contains("InventoryReserved"));
        verify(reservationLineRepository).saveAll(any());
    }

    @Test
    void insufficientStockRejectsWholeOrderWithoutChangingStock() {
        when(reservationRepository.existsByOrderId("order-1")).thenReturn(false);
        when(reservationRepository.saveAndFlush(any(InventoryReservation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryRepository.findByProductIdForUpdate("product-1")).thenReturn(Optional.of(inventory));

        reservationService.reserveForOrder(orderEvent(11));

        assertEquals(0, inventory.getReservedQuantity());
        verify(inventoryRepository, never()).saveAll(any());
        ArgumentCaptor<InventoryReservation> reservation = ArgumentCaptor.forClass(InventoryReservation.class);
        verify(reservationRepository).save(reservation.capture());
        assertEquals(ReservationStatus.REJECTED, reservation.getValue().getStatus());
        ArgumentCaptor<InventoryOutboxMessage> message = ArgumentCaptor.forClass(InventoryOutboxMessage.class);
        verify(outboxRepository).save(message.capture());
        assertTrue(message.getValue().getPayload().contains("InventoryReservationFailed"));
    }

    @Test
    void duplicateOrderEventDoesNotReserveTwice() {
        when(reservationRepository.existsByOrderId("order-1")).thenReturn(true);

        reservationService.reserveForOrder(orderEvent(4));

        verify(inventoryRepository, never()).findByProductIdForUpdate("product-1");
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void cancellationReleasesReservedStockOnce() {
        inventory.setReservedQuantity(4);
        InventoryReservation reservation = new InventoryReservation("order-1", ReservationStatus.RESERVED, null);
        when(reservationRepository.findByOrderId("order-1")).thenReturn(Optional.of(reservation));
        when(reservationLineRepository.findAllByOrderIdOrderByProductIdAsc("order-1"))
                .thenReturn(List.of(new com.ecommerce.inventory.model.InventoryReservationLine("order-1", "product-1", 4)));
        when(inventoryRepository.findByProductIdForUpdate("product-1")).thenReturn(Optional.of(inventory));

        reservationService.releaseForOrder("order-1");

        assertEquals(0, inventory.getReservedQuantity());
        assertEquals(ReservationStatus.RELEASED, reservation.getStatus());
    }

    @Test
    void confirmationDeductsStockAndReservation() {
        inventory.setReservedQuantity(4);
        InventoryReservation reservation = new InventoryReservation("order-1", ReservationStatus.RESERVED, null);
        when(reservationRepository.findByOrderId("order-1")).thenReturn(Optional.of(reservation));
        when(reservationLineRepository.findAllByOrderIdOrderByProductIdAsc("order-1"))
                .thenReturn(List.of(new com.ecommerce.inventory.model.InventoryReservationLine("order-1", "product-1", 4)));
        when(inventoryRepository.findByProductIdForUpdate("product-1")).thenReturn(Optional.of(inventory));

        reservationService.commitForOrder("order-1");

        assertEquals(6, inventory.getQuantity());
        assertEquals(0, inventory.getReservedQuantity());
        assertEquals(ReservationStatus.COMMITTED, reservation.getStatus());
    }

    private OrderEvent orderEvent(int quantity) {
        return new OrderEvent("OrderCreated", "event-1", "order-1", List.of(new OrderEventItem("product-1", quantity)));
    }
}
```

---

## File: `services/inventory-service/src/test/resources/application.properties`

```properties
spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.flyway.enabled=false
app.jwt.secret=test-only-inventory-jwt-secret-with-at-least-32-bytes

```
