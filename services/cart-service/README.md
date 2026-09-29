# Cart Service

Cart Service stores each user's cart in Redis with a configurable TTL. It stores product IDs and quantities; product names and prices are read from Catalog Service when returning a cart.

## Configuration

| Variable | Purpose | Local default |
|---|---|---|
| `APP_JWT_SECRET` | Verifies the HS256 access tokens issued by Auth Service. Use the same secret in both services. | Development-only secret |
| `REDIS_HOST` | Redis hostname | `localhost` |
| `REDIS_PORT` | Redis port | `6379` |
| `REDIS_PASSWORD` | Redis password | `redis_dev_password` |
| `CART_TTL_SECONDS` | Expiration after the most recent cart write | `86400` |
| `CATALOG_SERVICE_URL` | Catalog Service base URL | `http://localhost:8081` |

Docker Compose supplies the shared JWT secret and Redis password from the project-level environment file.

## API

All `/api/carts/{user_id}` routes require an Auth Service bearer token whose `sub` matches `user_id`. `/health` is public.

- `GET /api/carts/{user_id}` returns the cart with current product names and prices.
- `POST /api/carts/{user_id}/items` accepts `{"product_id":"...","quantity":1}`. The service verifies the product with Catalog Service; client-supplied names and prices are not used.
- `PATCH /api/carts/{user_id}/items/{product_id}` accepts `{"quantity":2}`.
- `DELETE /api/carts/{user_id}/items/{product_id}` removes one item.
- `DELETE /api/carts/{user_id}` clears the cart.
- `GET /api/carts/{user_id}/ttl` reports the remaining Redis TTL in seconds.

Each item quantity is limited to 1,000. Cart updates use Redis optimistic transactions and retry concurrent write conflicts up to five times before returning `409 Conflict`.

## Run locally

```bash
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

Set the variables above in the environment or in a local `.env` file before starting the service.
