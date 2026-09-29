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
