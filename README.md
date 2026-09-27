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
- notification-events

## Development phases

1. Foundations
2. Core commerce loop
3. Checkout and fulfillment
4. Discovery and engagement
5. Admin dashboard
6. Observability and resilience
7. AWS deployment
8. Portfolio polish
