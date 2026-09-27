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
