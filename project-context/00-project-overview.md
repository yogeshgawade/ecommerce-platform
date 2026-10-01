# 00-project-overview


---

## File: `docs/architecture.md`

```markdown
# E-Commerce Platform Architecture

## Current architecture

The platform uses independently deployable services with database ownership per service.

## Local infrastructure

- PostgreSQL for transactional services.
- MongoDB for flexible document data.
- Redis for carts, caching, and rate limiting.
- Kafka for asynchronous domain events.
- Elasticsearch for derived product search data.

## Communication

Use REST for synchronous request-response operations.

Use Kafka events for asynchronous state changes and workflow propagation.

## Initial event topics

- `order-events`
- `inventory-events`
- `payment-events`
- `catalog-events`
- `review-events` (review-service → search-service; keeps rating filters current)
- `notification-events`

## Development phases

1. Foundations
2. Core commerce loop
3. Checkout and fulfillment
4. Discovery and engagement
5. Admin dashboard
6. Observability and resilience
7. Cloud deployment
8. Portfolio polish

```

---

## File: `docs/development.md`

```markdown
# Development Conventions

## Service ownership

Each service owns its database. Other services must not directly access its tables or collections.

## Communication

Use synchronous REST when the caller needs an immediate response.

Use Kafka events for asynchronous state changes and workflows.

## Health endpoints

- Spring Boot services: `/actuator/health`
- FastAPI services: `/health`

## Initial Kafka topics

- `order-events`
- `inventory-events`
- `payment-events`
- `catalog-events`
- `review-events`
- `notification-events`

## API error format

```json
{
  "timestamp": "2026-09-27T00:00:00Z",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "path": "/api/products",
  "traceId": "distributed-trace-id"
}
```

```

---

## File: `README.md`

```markdown
# E-Commerce Platform

Production-grade e-commerce platform built with microservices.

## Current status

Phase 0 is complete:

- Local development repository created.
- PostgreSQL running with service databases.
- MongoDB running.
- Redis running.
- Kafka running with application topics.
- Elasticsearch running and healthy.
- Local infrastructure managed with Docker Compose.

## Architecture

### Core services

- auth-service
- catalog-service
- inventory-service
- order-service
- cart-service
- payment-service
- api-gateway

### Extended services

- search-service
- wishlist-service
- review-service

Search indexes catalog product events in Elasticsearch and is available through the API gateway at `GET /search`.

### Local infrastructure

- PostgreSQL
- MongoDB
- Redis
- Apache Kafka
- Elasticsearch

## Start local infrastructure

```bash
cp .env.example .env
docker compose --env-file .env -f infra/docker-compose.yml up -d
```

The Compose configuration reads PostgreSQL settings from the `ECOMMERCE_POSTGRES_*` keys in `.env`. This prevents generic exported `POSTGRES_*` variables in your shell from overriding the project's database credentials.

Check status:

```bash
docker compose --env-file .env -f infra/docker-compose.yml ps
```

Stop containers:

```bash
docker compose --env-file .env -f infra/docker-compose.yml down
```

Stop containers and delete local database volumes:

```bash
docker compose --env-file .env -f infra/docker-compose.yml down -v
```

## Local endpoints

| Component | Address |
|---|---|
| PostgreSQL | localhost:5432 |
| MongoDB | localhost:27017 |
| Redis | localhost:6379 |
| Kafka | localhost:9092 |
| Elasticsearch | http://localhost:9200 |

## Kafka topics

- order-events
- inventory-events
- payment-events
- catalog-events
- review-events
- notification-events

## Frontend

The React customer storefront and admin dashboard run from `frontend/`. See [frontend/README.md](frontend/README.md) for setup, Stripe test-mode configuration, and local commands. The customer app uses `http://localhost:3000`; the admin dashboard uses `http://localhost:3001`.

## Development phases

1. Foundations
2. Core commerce loop
3. Checkout and fulfillment
4. Discovery and engagement
5. Admin dashboard
6. Observability and resilience
7. AWS deployment
8. Portfolio polish

```
