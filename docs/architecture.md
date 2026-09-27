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
