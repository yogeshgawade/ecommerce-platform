# Wishlist Service

FastAPI service that stores each customer's saved product references in its own MongoDB database. User identity comes from the verified HS256 JWT `sub` claim. The client cannot select another user's wishlist.

## API

All wishlist routes require `Authorization: Bearer <access-token>`.

- `GET /api/wishlist?page=0&size=20` returns the caller's saved product IDs and their added times, with pagination.
- `PUT /api/wishlist/items/{product_id}` validates the product against Catalog Service and saves it. Repeating the request is idempotent.
- `DELETE /api/wishlist/items/{product_id}` removes the product. Repeating the request is safe.
- `DELETE /api/wishlist` clears the caller's wishlist.
- `GET /health` checks MongoDB.

Wishlist storage owns product references only; current product information remains owned by Catalog Service. The unique MongoDB index on `(userId, productId)` prevents duplicate items, including concurrent duplicate adds.

## Configuration

| Variable | Purpose | Local default |
|---|---|---|
| `MONGODB_URI` | MongoDB connection string; URI must include the wishlist database name | `mongodb://admin:mongo_dev_password@localhost:27017/wishlist_db?authSource=admin&directConnection=true` |
| `APP_JWT_SECRET` | Verifies Auth Service HS256 access tokens; must match gateway and Auth Service | Development-only secret |
| `CATALOG_SERVICE_URL` | Catalog Service base URL used to check products before saving | `http://localhost:8081` |
| `WISHLIST_MAX_PAGE_SIZE` | Maximum page size | `100` |

Docker Compose uses `MONGO_ROOT_USERNAME`, `MONGO_ROOT_PASSWORD`, `MONGO_WISHLIST_DB`, and `JWT_SECRET` from the root `.env` file.

## Run locally

```bash
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8004
```

Run the unit tests from this directory with:

```bash
pip install -r requirements-dev.txt
pytest -q
```
