from contextlib import asynccontextmanager

from elasticsearch import ApiError, Elasticsearch
from fastapi import FastAPI, HTTPException, Query
from elastic_transport import TransportError

from .config import settings
from .kafka_runtime import KafkaRuntime
from .schemas import SearchResponse
from .search_index import SearchIndex


@asynccontextmanager
async def lifespan(app: FastAPI):
    client = Elasticsearch(settings.elasticsearch_url, request_timeout=5)
    search_index = SearchIndex(client)
    search_index.ensure_index()
    runtime = KafkaRuntime(search_index)
    runtime.start()
    app.state.elasticsearch = client
    app.state.search_index = search_index
    app.state.kafka_runtime = runtime
    try:
        yield
    finally:
        runtime.stop()
        client.close()


app = FastAPI(title="Search Service", version="1.0.0", lifespan=lifespan)


@app.get("/health")
def health():
    client: Elasticsearch | None = getattr(app.state, "elasticsearch", None)
    runtime: KafkaRuntime | None = getattr(app.state, "kafka_runtime", None)
    try:
        elasticsearch_ok = bool(client and client.ping())
    except (ApiError, TransportError):
        elasticsearch_ok = False
    kafka_ok = not settings.kafka_enabled or bool(runtime and runtime.running)
    status = "ok" if elasticsearch_ok and kafka_ok else "unavailable"
    if status != "ok":
        raise HTTPException(status_code=503, detail={
            "status": status,
            "elasticsearch": elasticsearch_ok,
            "kafkaConsumer": kafka_ok,
        })
    return {"status": status, "elasticsearch": elasticsearch_ok, "kafkaConsumer": kafka_ok}


@app.get("/search", response_model=SearchResponse)
def search_products(
    q: str | None = Query(default=None, max_length=200),
    category: str | None = Query(default=None, max_length=100),
    brand: str | None = Query(default=None, max_length=100),
    min_price: float | None = Query(default=None, ge=0),
    max_price: float | None = Query(default=None, ge=0),
    min_rating: float | None = Query(default=None, ge=0, le=5),
    page: int = Query(default=0, ge=0),
    size: int = Query(default=20, ge=1, le=settings.max_page_size),
    sort: str = Query(
        default="relevance",
        pattern="^(relevance|price_asc|price_desc|name_asc|rating_desc)$",
    ),
):
    if min_price is not None and max_price is not None and min_price > max_price:
        raise HTTPException(status_code=400, detail="min_price must be less than or equal to max_price")

    query = q.strip() if q and q.strip() else None
    try:
        result = app.state.search_index.search(
            query=query,
            category=category.strip() if category else None,
            brand=brand.strip() if brand else None,
            min_price=min_price,
            max_price=max_price,
            min_rating=min_rating,
            page=page,
            size=size,
            sort=sort,
        )
    except (ApiError, TransportError) as exc:
        raise HTTPException(status_code=503, detail="Search index is temporarily unavailable") from exc
    return result
