# Search Service

FastAPI search API backed by Elasticsearch. It builds a derived product index from `ProductCreated`, `ProductUpdated`, and `ProductDeleted` messages on Kafka's `catalog-events` topic. It does not read the catalog database.

## Search API

`GET /search`

Supported query parameters:

- `q`: product name, brand, category, description, and attribute text query (up to 200 characters)
- `category`, `brand`: exact filters
- `min_price`, `max_price`: inclusive price bounds
- `min_rating`: optional minimum rating, ready for review data when that service is available
- `page`, `size`: zero-based pagination; size is capped at 100
- `sort`: `relevance`, `price_asc`, `price_desc`, `name_asc`, or `rating_desc`

The response includes matching products, pagination totals, and category, brand, price-range, and rating facets. Catalog currently has no rating field, so rating filters and facets only return useful data once ratings are supplied by the review integration.

Example:

```bash
curl 'http://localhost:8080/search?q=running%20shoe&category=footwear&min_price=20&max_price=150&page=0&size=20&sort=price_asc'
```

## Local service configuration

The service expects `ELASTICSEARCH_URL` (default `http://localhost:9200`), `KAFKA_BOOTSTRAP_SERVERS` (default `localhost:9092`), `KAFKA_CATALOG_TOPIC` (default `catalog-events`), and `KAFKA_CONSUMER_GROUP_ID` (default `search-service-group`). It starts with Kafka offset reset set to `earliest`, so a fresh consumer group can build its index from retained catalog events. Product IDs are Elasticsearch document IDs, making create/update/delete processing idempotent.

Run locally with:

```bash
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8003
```

Run the unit tests from this directory with:

```bash
pip install -r requirements-dev.txt
pytest -q
```

Health is available at `/health`. In Docker Compose, the service is internal to the application network and the gateway exposes its search route.
