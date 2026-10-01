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
