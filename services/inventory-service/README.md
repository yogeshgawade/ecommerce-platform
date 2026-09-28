# Inventory Service

Spring Boot service that owns PostgreSQL stock levels and participates in the order saga. It reserves stock when an order is created, releases it when an order is cancelled, and deducts it when an order is confirmed.

## Local setup

From the repository root, create `.env` from `.env.example`, then run:

```bash
docker compose --env-file .env -f infra/docker-compose.yml up -d --build inventory-service
```

Flyway initializes the inventory schema when the service connects to PostgreSQL. `spring.jpa.hibernate.ddl-auto=validate` checks that the migration and entity mappings agree.

## REST API

- `GET /api/inventory` lists stock records.
- `GET /api/inventory/{productId}` returns stock and available quantity.
- `PUT /api/inventory/{productId}` creates or sets total stock. The quantity cannot be reduced below currently reserved stock.
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

Failure uses `InventoryReservationFailed` and includes a human-readable `reason`. The outbox retries unpublished events after restart or Kafka outages. Consumers should still be idempotent because delivery is at least once.

## Configuration

Environment variables include `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`, `APP_JWT_SECRET`, `KAFKA_ORDER_TOPIC`, `KAFKA_INVENTORY_TOPIC`, and `KAFKA_OUTBOX_POLL_INTERVAL`. Docker Compose supplies the database credentials, JWT signing secret, and internal Kafka address.