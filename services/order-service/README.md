# Order Service

The order service owns order records and immutable product/price snapshots. It creates orders from catalog prices, then coordinates checkout through Kafka events. It does not read another service's database or accept prices from the client.

## API

All endpoints require a valid JWT with a `CUSTOMER` or `ADMIN` role.

### Create an order

`POST /api/orders` requires an `Idempotency-Key` header. Repeating the same request with the same key returns the existing order; reusing that key for a different request returns `409 Conflict`.

```json
{
  "items": [
    { "productId": "catalog-product-id", "quantity": 2 }
  ],
  "paymentMethodId": "pm_test_reference"
}
```

The response is `202 Accepted`. It includes an `orderId`, status, item/price snapshots, total, and timestamps. Payment method references are never returned. A new order starts at `PENDING_INVENTORY`.

### Read, list, and cancel

- `GET /api/orders/{orderId}` returns the caller's order. Admins can read any order.
- `GET /api/orders?page=0&size=20` returns only the caller's orders. Admins may pass `userId` or omit it to list all orders.
- `PATCH /api/orders/{orderId}/cancel` cancels an order while it is awaiting inventory reservation. The cancellation is published so inventory can release any reservation created concurrently.

## Saga events

- `OrderCreated` is written with the order in the database and published to `order-events` through an outbox.
- `InventoryReserved` moves the order to `PENDING_PAYMENT` and emits `PaymentRequested` to `payment-events`, including order total, currency, customer ID, and the provided payment-method reference.
- `InventoryReservationFailed` cancels the order.
- `PaymentCompleted` confirms the order and emits `OrderConfirmed` for inventory to deduct the reservation.
- `PaymentFailed` cancels the order and emits `OrderCancelled` for inventory compensation.
- Order lifecycle events carry the customer's email from the signed JWT so the notification service can deliver confirmation or cancellation messages without reading the auth database.

Outbox rows retry with exponential backoff. Event IDs and order state transitions make duplicate deliveries idempotent.

## Local run

From this directory, run `./gradlew bootRun`. The service uses PostgreSQL `order_db`, Kafka topics `order-events`, `inventory-events`, and `payment-events`, and the catalog HTTP API. `APP_JWT_SECRET` must match the auth service and gateway. The root Docker Compose stack builds and starts this service with the other local services.
