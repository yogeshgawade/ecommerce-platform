# API Gateway

Spring Cloud Gateway is the client-facing entry point. It validates JWT access tokens, applies role checks and Redis-backed request limits, and routes requests to internal services.

## Routes and access

| Path | Service | Access |
|---|---|---|
| `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout` | Auth Service | Public; Auth Service validates the submitted credentials or refresh token |
| `GET /auth/me` | Auth Service | Valid bearer token |
| `GET /api/products` and `/api/products/**` | Catalog Service | Public |
| `GET /search` | Search Service | Public |
| `POST /api/products` | Catalog Service | `ADMIN` |
| `PUT` or `DELETE /api/products/**` | Catalog Service | `ADMIN` |
| `/api/carts/**` | Cart Service | Valid bearer token |
| `/api/wishlist` and `/api/wishlist/**` | Wishlist Service | `CUSTOMER` or `ADMIN` bearer token; service also verifies JWT ownership |
| `GET /api/products/{id}/reviews` | Review Service | Public |
| `POST /api/products/{id}/reviews` | Review Service | `CUSTOMER` with a confirmed order containing the product |
| `PUT` or `DELETE /api/products/{id}/reviews/me` | Review Service | `CUSTOMER` |
| `/api/inventory/**` | Inventory Service | `ADMIN` |

The gateway and downstream services share `APP_JWT_SECRET` (wired from `JWT_SECRET` in Compose). The cart route also forwards the bearer token for service-level owner validation.

## Configuration

- `APP_CORS_ALLOWED_ORIGINS` is a comma-separated allowlist. The local defaults are `http://localhost:3000` and `http://localhost:3001`.
- Redis credentials and host settings configure the request limiter. Anonymous requests are keyed by peer IP; authenticated requests are keyed by the verified JWT subject. The resolver ignores caller-supplied identity headers.
- The default limiter allows 10 requests per second with a burst capacity of 20 per key.
- Only `/actuator/health` is exposed without authentication. Gateway route details are disabled on the public management endpoint.

## Run locally

Start Redis and the downstream services, then run:

```bash
./gradlew bootRun
```

Health check: `http://localhost:8080/actuator/health`.
