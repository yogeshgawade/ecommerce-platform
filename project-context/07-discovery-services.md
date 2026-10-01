# 07-discovery-services


---

## File: `services/review-service/app/auth.py`

```python
from dataclasses import dataclass

import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from jwt import InvalidTokenError

from .config import settings

if len(settings.app_jwt_secret.encode("utf-8")) < 32:
    raise RuntimeError("APP_JWT_SECRET must contain at least 32 bytes")

bearer_scheme = HTTPBearer(auto_error=False)


@dataclass(frozen=True)
class CustomerIdentity:
    user_id: str
    token: str


async def authenticated_customer(
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer_scheme),
) -> CustomerIdentity:
    if credentials is None or credentials.scheme.lower() != "bearer":
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Bearer token required",
            headers={"WWW-Authenticate": "Bearer"},
        )

    try:
        claims = jwt.decode(credentials.credentials, settings.app_jwt_secret, algorithms=["HS256"])
    except InvalidTokenError as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid bearer token",
            headers={"WWW-Authenticate": "Bearer"},
        ) from exc

    subject = claims.get("sub")
    roles = claims.get("roles")
    if not isinstance(subject, str) or not subject.strip() or not isinstance(roles, list):
        raise HTTPException(status_code=401, detail="Bearer token has invalid claims")
    if "CUSTOMER" not in roles:
        raise HTTPException(status_code=403, detail="Only customers can manage reviews")
    return CustomerIdentity(user_id=subject, token=credentials.credentials)

```

---

## File: `services/review-service/app/catalog_client.py`

```python
from urllib.parse import quote

import httpx
from fastapi import HTTPException, status
from pydantic import BaseModel, Field, ValidationError

from .config import settings


class CatalogProduct(BaseModel):
    id: str
    name: str = Field(min_length=1)


class CatalogClient:
    def __init__(self, client: httpx.AsyncClient, base_url: str = settings.catalog_service_url):
        self.client = client
        self.base_url = base_url.rstrip("/")

    async def ensure_product_exists(self, product_id: str) -> None:
        try:
            response = await self.client.get(
                f"{self.base_url}/api/products/{quote(product_id, safe='')}"
            )
        except httpx.RequestError as exc:
            raise HTTPException(status_code=503, detail="Catalog service is unavailable") from exc

        if response.status_code == status.HTTP_404_NOT_FOUND:
            raise HTTPException(status_code=404, detail="Product not found")
        if response.status_code >= 500:
            raise HTTPException(status_code=503, detail="Catalog service is unavailable")
        if response.status_code != status.HTTP_200_OK:
            raise HTTPException(status_code=502, detail="Catalog service returned an unexpected response")

        try:
            product = CatalogProduct.model_validate(response.json())
        except (ValueError, ValidationError) as exc:
            raise HTTPException(status_code=502, detail="Catalog service returned invalid product data") from exc
        if product.id != product_id:
            raise HTTPException(status_code=502, detail="Catalog service returned a mismatched product")

```

---

## File: `services/review-service/app/config.py`

```python
import os
from dataclasses import dataclass

from dotenv import load_dotenv

load_dotenv()


@dataclass(frozen=True)
class Settings:
    mongodb_uri: str = os.getenv(
        "MONGODB_URI",
        "mongodb://admin:mongo_dev_password@localhost:27017/review_db?authSource=admin&directConnection=true",
    )
    catalog_service_url: str = os.getenv("CATALOG_SERVICE_URL", "http://localhost:8081").rstrip("/")
    order_service_url: str = os.getenv("ORDER_SERVICE_URL", "http://localhost:8084").rstrip("/")
    app_jwt_secret: str = os.getenv(
        "APP_JWT_SECRET",
        "local-development-secret-change-this-to-a-long-random-value",
    )
    max_page_size: int = int(os.getenv("REVIEW_MAX_PAGE_SIZE", "100"))
    kafka_enabled: bool = os.getenv("KAFKA_ENABLED", "true").strip().lower() in {
        "1", "true", "yes", "on"
    }
    kafka_bootstrap_servers: str = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    kafka_topic: str = os.getenv("KAFKA_REVIEW_TOPIC", "review-events")


settings = Settings()

```

---

## File: `services/review-service/app/__init__.py`

```python


```

---

## File: `services/review-service/app/kafka_publisher.py`

```python
import json
import logging
from datetime import datetime, timezone
from uuid import uuid4

from confluent_kafka import Producer

from .config import settings

logger = logging.getLogger(__name__)


class RatingPublisher:
    def __init__(self):
        self.producer = Producer({"bootstrap.servers": settings.kafka_bootstrap_servers}) if settings.kafka_enabled else None

    def publish(self, product_id: str, average_rating: float | None, review_count: int) -> None:
        if self.producer is None:
            return
        event = {
            "eventId": str(uuid4()),
            "eventType": "ReviewRatingUpdated",
            "occurredAt": datetime.now(timezone.utc).isoformat(),
            "productId": product_id,
            "averageRating": average_rating,
            "reviewCount": review_count,
        }
        delivery_errors: list[str] = []

        def on_delivery(error, _message):
            if error is not None:
                delivery_errors.append(str(error))

        self.producer.produce(
            settings.kafka_topic,
            key=product_id,
            value=json.dumps(event, separators=(",", ":")),
            on_delivery=on_delivery,
        )
        remaining = self.producer.flush(5.0)
        if remaining:
            raise TimeoutError("Timed out publishing product rating update")
        if delivery_errors:
            raise RuntimeError(f"Could not publish product rating update: {delivery_errors[0]}")

    def close(self) -> None:
        if self.producer is not None:
            self.producer.flush(5.0)

```

---

## File: `services/review-service/app/main.py`

```python
import logging
from contextlib import asynccontextmanager

import httpx
from fastapi import Depends, FastAPI, HTTPException, Query, Response, status
from pymongo import AsyncMongoClient
from pymongo.errors import DuplicateKeyError, PyMongoError
from starlette.concurrency import run_in_threadpool

from .auth import CustomerIdentity, authenticated_customer
from .catalog_client import CatalogClient
from .config import settings
from .kafka_publisher import RatingPublisher
from .models import CreateReviewRequest, ReviewPageResponse, ReviewResponse, UpdateReviewRequest
from .order_client import OrderClient
from .repository import ReviewRepository

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    mongo_client = AsyncMongoClient(settings.mongodb_uri, serverSelectionTimeoutMS=5000, tz_aware=True)
    database = mongo_client.get_default_database()
    if database is None:
        raise RuntimeError("MONGODB_URI must include a database name")
    await mongo_client.admin.command("ping")
    repository = ReviewRepository(database.get_collection("reviews"))
    await repository.ensure_indexes()

    http_client = httpx.AsyncClient(timeout=httpx.Timeout(3.0, connect=1.0))
    app.state.mongo_client = mongo_client
    app.state.review_repository = repository
    app.state.catalog_client = CatalogClient(http_client)
    app.state.order_client = OrderClient(http_client)
    app.state.rating_publisher = RatingPublisher()
    try:
        yield
    finally:
        app.state.rating_publisher.close()
        await http_client.aclose()
        await mongo_client.close()


app = FastAPI(title="Review Service", version="1.0.0", lifespan=lifespan)


@app.get("/health")
async def health() -> dict[str, str]:
    mongo_client: AsyncMongoClient | None = getattr(app.state, "mongo_client", None)
    try:
        if mongo_client is None:
            raise PyMongoError("MongoDB client is not initialized")
        await mongo_client.admin.command("ping")
    except PyMongoError as exc:
        raise HTTPException(status_code=503, detail="MongoDB is unavailable") from exc
    return {"status": "UP"}


@app.get("/api/products/{product_id}/reviews", response_model=ReviewPageResponse)
async def list_product_reviews(
    product_id: str,
    page: int = Query(default=0, ge=0),
    size: int = Query(default=20, ge=1, le=settings.max_page_size),
) -> ReviewPageResponse:
    repository: ReviewRepository = app.state.review_repository
    reviews, total, average = await repository.get_page(product_id, page, size)
    return ReviewPageResponse(
        content=[ReviewRepository.to_response(review) for review in reviews],
        page=page,
        size=size,
        total_elements=total,
        total_pages=repository.total_pages(total, size),
        average_rating=average,
        review_count=total,
    )


@app.post("/api/products/{product_id}/reviews", response_model=ReviewResponse,
          status_code=status.HTTP_201_CREATED)
async def create_product_review(
    product_id: str,
    request: CreateReviewRequest,
    user: CustomerIdentity = Depends(authenticated_customer),
) -> ReviewResponse:
    if not product_id.strip() or len(product_id) > 200:
        raise HTTPException(status_code=422, detail="product_id must contain 1 to 200 characters")
    product_id = product_id.strip()
    await app.state.catalog_client.ensure_product_exists(product_id)
    await app.state.order_client.verify_confirmed_purchase(
        order_id=request.order_id,
        user_id=user.user_id,
        product_id=product_id,
        token=user.token,
    )
    repository: ReviewRepository = app.state.review_repository
    try:
        review = await repository.create(
            user_id=user.user_id,
            product_id=product_id,
            order_id=request.order_id,
            rating=request.rating,
            title=request.title,
            body=request.body,
        )
    except DuplicateKeyError as exc:
        raise HTTPException(status_code=409, detail="You have already reviewed this product") from exc
    average, count = await repository.get_summary(product_id)
    await _publish_rating(product_id, average, count)
    return ReviewResponse(**ReviewRepository.to_response(review))


@app.put("/api/products/{product_id}/reviews/me", response_model=ReviewResponse)
async def update_my_review(
    product_id: str,
    request: UpdateReviewRequest,
    user: CustomerIdentity = Depends(authenticated_customer),
) -> ReviewResponse:
    repository: ReviewRepository = app.state.review_repository
    review = await repository.update(
        user_id=user.user_id,
        product_id=product_id,
        rating=request.rating,
        title=request.title,
        body=request.body,
    )
    if review is None:
        raise HTTPException(status_code=404, detail="Review not found")
    average, count = await repository.get_summary(product_id)
    await _publish_rating(product_id, average, count)
    return ReviewResponse(**ReviewRepository.to_response(review))


@app.delete("/api/products/{product_id}/reviews/me", status_code=status.HTTP_204_NO_CONTENT)
async def delete_my_review(
    product_id: str,
    user: CustomerIdentity = Depends(authenticated_customer),
) -> Response:
    repository: ReviewRepository = app.state.review_repository
    deleted = await repository.delete(user_id=user.user_id, product_id=product_id)
    if not deleted:
        raise HTTPException(status_code=404, detail="Review not found")
    average, count = await repository.get_summary(product_id)
    await _publish_rating(product_id, average, count)
    return Response(status_code=status.HTTP_204_NO_CONTENT)


async def _publish_rating(product_id: str, average: float | None, count: int) -> None:
    publisher: RatingPublisher = app.state.rating_publisher
    try:
        await run_in_threadpool(publisher.publish, product_id, average, count)
    except Exception:
        # The review is already stored. Logging allows a retry by editing the review,
        # and keeps a temporary Kafka failure from turning a successful write into an error.
        logger.exception("Could not publish rating update for product %s", product_id)

```

---

## File: `services/review-service/app/models.py`

```python
from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field, field_validator


class CreateReviewRequest(BaseModel):
    order_id: str = Field(min_length=1, max_length=128)
    rating: int = Field(ge=1, le=5)
    title: str = Field(min_length=1, max_length=120)
    body: str = Field(min_length=1, max_length=3000)

    @field_validator("order_id", "title", "body")
    @classmethod
    def trim_required_text(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise ValueError("value must not be blank")
        return normalized


class UpdateReviewRequest(BaseModel):
    rating: int = Field(ge=1, le=5)
    title: str = Field(min_length=1, max_length=120)
    body: str = Field(min_length=1, max_length=3000)

    @field_validator("title", "body")
    @classmethod
    def trim_required_text(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise ValueError("value must not be blank")
        return normalized


class ReviewResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    review_id: str
    product_id: str
    rating: int
    title: str
    body: str
    verified_purchase: bool
    created_at: datetime
    updated_at: datetime


class ReviewSummary(BaseModel):
    average_rating: float | None
    review_count: int


class ReviewPageResponse(BaseModel):
    content: list[ReviewResponse]
    page: int
    size: int
    total_elements: int
    total_pages: int
    average_rating: float | None
    review_count: int

```

---

## File: `services/review-service/app/order_client.py`

```python
from urllib.parse import quote

import httpx
from fastapi import HTTPException
from pydantic import BaseModel, ConfigDict, Field, ValidationError

from .config import settings


class OrderItem(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    product_id: str = Field(alias="productId")


class CustomerOrder(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    order_id: str = Field(alias="orderId")
    user_id: str = Field(alias="userId")
    status: str
    items: list[OrderItem]


class OrderClient:
    def __init__(self, client: httpx.AsyncClient, base_url: str = settings.order_service_url):
        self.client = client
        self.base_url = base_url.rstrip("/")

    async def verify_confirmed_purchase(
        self, *, order_id: str, user_id: str, product_id: str, token: str
    ) -> None:
        try:
            response = await self.client.get(
                f"{self.base_url}/api/orders/{quote(order_id, safe='')}",
                headers={"Authorization": f"Bearer {token}"},
            )
        except httpx.RequestError as exc:
            raise HTTPException(status_code=503, detail="Order service is unavailable") from exc

        if response.status_code == 404:
            raise HTTPException(status_code=404, detail="Confirmed order not found")
        if response.status_code >= 500:
            raise HTTPException(status_code=503, detail="Order service is unavailable")
        if response.status_code != 200:
            raise HTTPException(status_code=502, detail="Order service returned an unexpected response")

        try:
            order = CustomerOrder.model_validate(response.json())
        except (ValueError, ValidationError) as exc:
            raise HTTPException(status_code=502, detail="Order service returned invalid order data") from exc
        if order.order_id != order_id or order.user_id != user_id:
            raise HTTPException(status_code=502, detail="Order service returned mismatched order data")
        if order.status != "CONFIRMED" or product_id not in {item.product_id for item in order.items}:
            raise HTTPException(status_code=403, detail="A confirmed purchase of this product is required")

```

---

## File: `services/review-service/app/repository.py`

```python
from datetime import datetime, timezone
from math import ceil
from typing import Any

from pymongo import ASCENDING, DESCENDING, ReturnDocument


class ReviewRepository:
    def __init__(self, reviews: Any):
        self.reviews = reviews

    async def ensure_indexes(self) -> None:
        await self.reviews.create_index(
            [("userId", ASCENDING), ("productId", ASCENDING)],
            unique=True,
            name="uq_review_user_product",
        )
        await self.reviews.create_index(
            [("productId", ASCENDING), ("createdAt", DESCENDING)],
            name="ix_review_product_created_at",
        )

    async def create(self, *, user_id: str, product_id: str, order_id: str,
                     rating: int, title: str, body: str) -> dict[str, Any]:
        now = datetime.now(timezone.utc)
        document = {
            "userId": user_id,
            "productId": product_id,
            "orderId": order_id,
            "rating": rating,
            "title": title,
            "body": body,
            "verifiedPurchase": True,
            "createdAt": now,
            "updatedAt": now,
        }
        result = await self.reviews.insert_one(document)
        document["_id"] = result.inserted_id
        return document

    async def update(self, *, user_id: str, product_id: str,
                     rating: int, title: str, body: str) -> dict[str, Any] | None:
        return await self.reviews.find_one_and_update(
            {"userId": user_id, "productId": product_id},
            {"$set": {
                "rating": rating,
                "title": title,
                "body": body,
                "updatedAt": datetime.now(timezone.utc),
            }},
            return_document=ReturnDocument.AFTER,
        )

    async def delete(self, *, user_id: str, product_id: str) -> bool:
        result = await self.reviews.delete_one({"userId": user_id, "productId": product_id})
        return result.deleted_count > 0

    async def get_page(self, product_id: str, page: int, size: int) -> tuple[list[dict[str, Any]], int, float | None]:
        query = {"productId": product_id}
        total = await self.reviews.count_documents(query)
        cursor = (
            self.reviews.find(query)
            .sort("createdAt", DESCENDING)
            .skip(page * size)
            .limit(size)
        )
        reviews = await cursor.to_list(length=size)
        summary_cursor = await self.reviews.aggregate([
            {"$match": query},
            {
                "$group": {
                    "_id": None,
                    "averageRating": {"$avg": "$rating"},
                    "reviewCount": {"$sum": 1},
                }
            },
        ])
        summary = await summary_cursor.to_list(length=1)
        average = round(float(summary[0]["averageRating"]), 2) if summary else None
        return reviews, total, average

    async def get_summary(self, product_id: str) -> tuple[float | None, int]:
        summary_cursor = await self.reviews.aggregate([
            {"$match": {"productId": product_id}},
            {
                "$group": {
                    "_id": None,
                    "averageRating": {"$avg": "$rating"},
                    "reviewCount": {"$sum": 1},
                }
            },
        ])
        summary = await summary_cursor.to_list(length=1)
        if not summary:
            return None, 0
        return round(float(summary[0]["averageRating"]), 2), int(summary[0]["reviewCount"])

    @staticmethod
    def to_response(document: dict[str, Any]) -> dict[str, Any]:
        return {
            "review_id": str(document["_id"]),
            "product_id": document["productId"],
            "rating": document["rating"],
            "title": document["title"],
            "body": document["body"],
            "verified_purchase": document["verifiedPurchase"],
            "created_at": document["createdAt"],
            "updated_at": document["updatedAt"],
        }

    @staticmethod
    def total_pages(total: int, size: int) -> int:
        return ceil(total / size) if total else 0

```

---

## File: `services/review-service/.pytest_cache/README.md`

```markdown
# pytest cache directory #

This directory contains data from the pytest's cache plugin,
which provides the `--lf` and `--ff` options, as well as the `cache` fixture.

**Do not** commit this to version control.

See [the docs](https://docs.pytest.org/en/stable/how-to/cache.html) for more information.

```

---

## File: `services/review-service/pytest.ini`

```properties
[pytest]
asyncio_mode = auto

```

---

## File: `services/review-service/tests/test_auth.py`

```python
import jwt
import pytest
from fastapi import HTTPException
from fastapi.security import HTTPAuthorizationCredentials

from app.auth import authenticated_customer
from app.config import settings


def bearer(token: str) -> HTTPAuthorizationCredentials:
    return HTTPAuthorizationCredentials(scheme="Bearer", credentials=token)


@pytest.mark.asyncio
async def test_authenticated_customer_returns_subject_and_token():
    token = jwt.encode(
        {"sub": "customer-123", "roles": ["CUSTOMER"]},
        settings.app_jwt_secret,
        algorithm="HS256",
    )

    identity = await authenticated_customer(bearer(token))

    assert identity.user_id == "customer-123"
    assert identity.token == token


@pytest.mark.asyncio
async def test_authenticated_customer_rejects_missing_credentials():
    with pytest.raises(HTTPException) as error:
        await authenticated_customer(None)

    assert error.value.status_code == 401
    assert error.value.headers["WWW-Authenticate"] == "Bearer"


@pytest.mark.asyncio
async def test_authenticated_customer_rejects_bad_signature():
    token = jwt.encode(
        {"sub": "customer-123", "roles": ["CUSTOMER"]},
        "a-different-secret-with-sufficient-length-for-hs256",
        algorithm="HS256",
    )

    with pytest.raises(HTTPException) as error:
        await authenticated_customer(bearer(token))

    assert error.value.status_code == 401


@pytest.mark.asyncio
async def test_authenticated_customer_rejects_non_customer_role():
    token = jwt.encode(
        {"sub": "admin-123", "roles": ["ADMIN"]},
        settings.app_jwt_secret,
        algorithm="HS256",
    )

    with pytest.raises(HTTPException) as error:
        await authenticated_customer(bearer(token))

    assert error.value.status_code == 403


@pytest.mark.asyncio
async def test_authenticated_customer_rejects_missing_roles():
    token = jwt.encode({"sub": "customer-123"}, settings.app_jwt_secret, algorithm="HS256")

    with pytest.raises(HTTPException) as error:
        await authenticated_customer(bearer(token))

    assert error.value.status_code == 401

```

---

## File: `services/review-service/tests/test_catalog_client.py`

```python
import httpx
import pytest
from fastapi import HTTPException

from app.catalog_client import CatalogClient


@pytest.mark.asyncio
async def test_catalog_client_checks_escaped_product_path_and_response():
    requests = []

    def handler(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        return httpx.Response(200, json={"id": "product/1", "name": "Shoe"})

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        await CatalogClient(client, "http://catalog").ensure_product_exists("product/1")

    assert requests[0].url.raw_path == b"/api/products/product%2F1"


@pytest.mark.asyncio
async def test_catalog_client_maps_missing_product_to_404():
    transport = httpx.MockTransport(lambda _request: httpx.Response(404))
    async with httpx.AsyncClient(transport=transport) as client:
        with pytest.raises(HTTPException) as error:
            await CatalogClient(client, "http://catalog").ensure_product_exists("missing")

    assert error.value.status_code == 404


@pytest.mark.asyncio
async def test_catalog_client_maps_upstream_failure_to_503():
    transport = httpx.MockTransport(lambda _request: httpx.Response(503))
    async with httpx.AsyncClient(transport=transport) as client:
        with pytest.raises(HTTPException) as error:
            await CatalogClient(client, "http://catalog").ensure_product_exists("product-1")

    assert error.value.status_code == 503


@pytest.mark.asyncio
async def test_catalog_client_rejects_mismatched_product_id():
    transport = httpx.MockTransport(
        lambda _request: httpx.Response(200, json={"id": "other", "name": "Shoe"})
    )
    async with httpx.AsyncClient(transport=transport) as client:
        with pytest.raises(HTTPException) as error:
            await CatalogClient(client, "http://catalog").ensure_product_exists("product-1")

    assert error.value.status_code == 502

```

---

## File: `services/review-service/tests/test_models.py`

```python
import pytest
from pydantic import ValidationError

from app.models import CreateReviewRequest, UpdateReviewRequest


def test_create_review_trims_required_text():
    request = CreateReviewRequest(
        order_id=" order-1 ", rating=5, title=" Great ", body=" Good fit "
    )

    assert request.order_id == "order-1"
    assert request.title == "Great"
    assert request.body == "Good fit"


@pytest.mark.parametrize("field,value", [("order_id", "  "), ("title", " "), ("body", "\n")])
def test_create_review_rejects_blank_required_text(field, value):
    payload = {"order_id": "order-1", "rating": 4, "title": "Nice", "body": "Works"}
    payload[field] = value

    with pytest.raises(ValidationError):
        CreateReviewRequest(**payload)


@pytest.mark.parametrize("rating", [0, 6, -1])
def test_review_requests_reject_rating_outside_one_to_five(rating):
    with pytest.raises(ValidationError):
        UpdateReviewRequest(rating=rating, title="Nice", body="Works")


def test_update_review_rejects_blank_text():
    with pytest.raises(ValidationError):
        UpdateReviewRequest(rating=4, title=" Nice ", body="   ")

```

---

## File: `services/review-service/tests/test_order_client.py`

```python
import httpx
import pytest
from fastapi import HTTPException

from app.order_client import OrderClient


def response(payload, status_code=200):
    return httpx.Response(status_code, json=payload)


@pytest.mark.asyncio
async def test_order_client_forwards_bearer_and_accepts_confirmed_product_purchase():
    requests = []
    order = {
        "orderId": "order-1",
        "userId": "customer-1",
        "status": "CONFIRMED",
        "items": [{"productId": "product-1", "quantity": 1}],
    }

    def handler(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        return response(order)

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        await OrderClient(client, "http://orders").verify_confirmed_purchase(
            order_id="order-1", user_id="customer-1", product_id="product-1", token="jwt-value"
        )

    assert requests[0].headers["Authorization"] == "Bearer jwt-value"
    assert requests[0].url.path == "/api/orders/order-1"


@pytest.mark.parametrize(
    "order",
    [
        {"orderId": "order-1", "userId": "someone-else", "status": "CONFIRMED", "items": [{"productId": "product-1"}]},
        {"orderId": "order-1", "userId": "customer-1", "status": "PENDING", "items": [{"productId": "product-1"}]},
        {"orderId": "order-1", "userId": "customer-1", "status": "CONFIRMED", "items": [{"productId": "another-product"}]},
    ],
)
@pytest.mark.asyncio
async def test_order_client_rejects_unowned_unconfirmed_or_unrelated_order(order):
    transport = httpx.MockTransport(lambda _request: response(order))
    async with httpx.AsyncClient(transport=transport) as client:
        with pytest.raises(HTTPException) as error:
            await OrderClient(client, "http://orders").verify_confirmed_purchase(
                order_id="order-1", user_id="customer-1", product_id="product-1", token="jwt"
            )

    assert error.value.status_code in {403, 502}


@pytest.mark.asyncio
async def test_order_client_maps_not_found_and_unavailable_responses():
    for status_code, expected in [(404, 404), (503, 503)]:
        transport = httpx.MockTransport(lambda _request, code=status_code: response({}, code))
        async with httpx.AsyncClient(transport=transport) as client:
            with pytest.raises(HTTPException) as error:
                await OrderClient(client, "http://orders").verify_confirmed_purchase(
                    order_id="order-1", user_id="customer-1", product_id="product-1", token="jwt"
                )
        assert error.value.status_code == expected

```

---

## File: `services/review-service/tests/test_repository.py`

```python
from datetime import datetime, timezone

import pytest
from bson import ObjectId
from pymongo import ASCENDING, DESCENDING, ReturnDocument

from app.repository import ReviewRepository


class FakeCursor:
    def __init__(self, documents):
        self.documents = documents
        self.sort_args = None
        self.skip_count = None
        self.limit_count = None

    def sort(self, *args):
        self.sort_args = args
        return self

    def skip(self, count):
        self.skip_count = count
        return self

    def limit(self, count):
        self.limit_count = count
        return self

    async def to_list(self, length):
        return self.documents[:length]


class FakeCollection:
    def __init__(self):
        self.index_calls = []
        self.inserted = None
        self.inserted_id = ObjectId()
        self.update_call = None
        self.update_result = None
        self.delete_query = None
        self.deleted_count = 0
        self.total_count = 0
        self.cursor = FakeCursor([])
        self.aggregations = []
        self.aggregate_results = []

    async def create_index(self, keys, **kwargs):
        self.index_calls.append((keys, kwargs))

    async def insert_one(self, document):
        self.inserted = document.copy()

        class Result:
            inserted_id = self.inserted_id

        return Result()

    async def find_one_and_update(self, query, update, **kwargs):
        self.update_call = (query, update, kwargs)
        return self.update_result

    async def delete_one(self, query):
        self.delete_query = query

        class Result:
            deleted_count = self.deleted_count

        return Result()

    async def count_documents(self, query):
        self.count_query = query
        return self.total_count

    def find(self, query):
        self.find_query = query
        return self.cursor

    def aggregate(self, pipeline):
        self.aggregations.append(pipeline)

        class AggregateCursor:
            def __init__(self, results):
                self.results = results

            async def to_list(self, length):
                return self.results[:length]

        return AggregateCursor(self.aggregate_results)


@pytest.mark.asyncio
async def test_ensure_indexes_enforces_one_review_per_user_and_product():
    collection = FakeCollection()
    await ReviewRepository(collection).ensure_indexes()

    assert collection.index_calls[0][0] == [("userId", ASCENDING), ("productId", ASCENDING)]
    assert collection.index_calls[0][1]["unique"] is True
    assert collection.index_calls[0][1]["name"] == "uq_review_user_product"
    assert collection.index_calls[1][0] == [("productId", ASCENDING), ("createdAt", DESCENDING)]


@pytest.mark.asyncio
async def test_create_marks_review_verified_and_sets_timestamps():
    collection = FakeCollection()
    document = await ReviewRepository(collection).create(
        user_id="user-1", product_id="product-1", order_id="order-1",
        rating=5, title="Great", body="Works well",
    )

    assert collection.inserted["verifiedPurchase"] is True
    assert collection.inserted["userId"] == "user-1"
    assert collection.inserted["orderId"] == "order-1"
    assert document["_id"] == collection.inserted_id
    assert document["createdAt"].tzinfo == timezone.utc
    assert document["updatedAt"] == document["createdAt"]


@pytest.mark.asyncio
async def test_update_is_scoped_to_owner_and_product():
    collection = FakeCollection()
    collection.update_result = {"_id": ObjectId(), "rating": 4}
    await ReviewRepository(collection).update(
        user_id="user-1", product_id="product-1", rating=4, title="Good", body="Solid"
    )

    query, update, options = collection.update_call
    assert query == {"userId": "user-1", "productId": "product-1"}
    assert update["$set"]["rating"] == 4
    assert options["return_document"] == ReturnDocument.AFTER
    assert "updatedAt" in update["$set"]


@pytest.mark.asyncio
async def test_delete_is_scoped_to_owner_and_product():
    collection = FakeCollection()
    collection.deleted_count = 1

    deleted = await ReviewRepository(collection).delete(user_id="user-1", product_id="product-1")

    assert deleted is True
    assert collection.delete_query == {"userId": "user-1", "productId": "product-1"}


@pytest.mark.asyncio
async def test_get_page_applies_sort_pagination_and_aggregate():
    review = {"_id": ObjectId(), "productId": "product-1", "rating": 4}
    collection = FakeCollection()
    collection.total_count = 25
    collection.cursor = FakeCursor([review])
    collection.aggregate_results = [{"averageRating": 4.25, "reviewCount": 25}]

    result = await ReviewRepository(collection).get_page("product-1", page=2, size=10)

    assert collection.find_query == {"productId": "product-1"}
    assert collection.cursor.sort_args == ("createdAt", DESCENDING)
    assert collection.cursor.skip_count == 20
    assert collection.cursor.limit_count == 10
    assert result == ([review], 25, 4.25)


@pytest.mark.asyncio
async def test_get_summary_returns_empty_when_no_reviews():
    collection = FakeCollection()

    assert await ReviewRepository(collection).get_summary(product_id="product-1") == (None, 0)


@pytest.mark.parametrize("total,size,expected", [(0, 20, 0), (1, 20, 1), (20, 20, 1), (21, 20, 2)])
def test_total_pages(total, size, expected):
    assert ReviewRepository.total_pages(total, size) == expected


def test_to_response_serializes_review_document():
    now = datetime.now(timezone.utc)
    document = {
        "_id": ObjectId("64a000000000000000000001"),
        "productId": "product-1",
        "rating": 5,
        "title": "Great",
        "body": "Very good",
        "verifiedPurchase": True,
        "createdAt": now,
        "updatedAt": now,
    }

    response = ReviewRepository.to_response(document)

    assert response["review_id"] == str(document["_id"])
    assert response["product_id"] == "product-1"
    assert response["verified_purchase"] is True

```

---

## File: `services/review-service/tests/test_routes.py`

```python
from datetime import datetime, timezone
from types import SimpleNamespace

import httpx
import pytest
from bson import ObjectId
from fastapi import HTTPException
from pymongo.errors import DuplicateKeyError

from app import main as review_app
from app.auth import CustomerIdentity, authenticated_customer
from app.models import CreateReviewRequest, UpdateReviewRequest
app = review_app.app


def review_document(*, user_id="customer-1", product_id="product-1", rating=5):
    now = datetime(2026, 1, 1, tzinfo=timezone.utc)
    return {
        "_id": ObjectId("64a000000000000000000001"),
        "userId": user_id,
        "productId": product_id,
        "orderId": "order-1",
        "rating": rating,
        "title": "Great",
        "body": "Works well",
        "verifiedPurchase": True,
        "createdAt": now,
        "updatedAt": now,
    }


class FakeRepository:
    def __init__(self):
        self.page_result = ([review_document()], 1, 5.0)
        self.created = review_document()
        self.updated = review_document(rating=4)
        self.summary = (5.0, 1)
        self.deleted = True
        self.create_error = None
        self.create_calls = []
        self.update_calls = []
        self.delete_calls = []

    async def get_page(self, product_id, page, size):
        self.page_call = (product_id, page, size)
        return self.page_result

    async def create(self, **kwargs):
        self.create_calls.append(kwargs)
        if self.create_error:
            raise self.create_error
        return self.created

    async def update(self, **kwargs):
        self.update_calls.append(kwargs)
        return self.updated

    async def delete(self, user_id, product_id):
        self.delete_calls.append((user_id, product_id))
        return self.deleted

    async def get_summary(self, product_id):
        self.summary_product_id = product_id
        return self.summary

    @staticmethod
    def total_pages(total, size):
        return (total + size - 1) // size if total else 0


class FakeCatalogClient:
    def __init__(self):
        self.calls = []
        self.error = None

    async def ensure_product_exists(self, product_id):
        self.calls.append(product_id)
        if self.error:
            raise self.error


class FakeOrderClient:
    def __init__(self):
        self.calls = []
        self.error = None

    async def verify_confirmed_purchase(self, **kwargs):
        self.calls.append(kwargs)
        if self.error:
            raise self.error


class FakePublisher:
    def __init__(self):
        self.calls = []

    def publish(self, *args):
        self.calls.append(args)


@pytest.fixture
def clients(monkeypatch):
    repository = FakeRepository()
    catalog = FakeCatalogClient()
    orders = FakeOrderClient()
    publisher = FakePublisher()
    app.state.review_repository = repository
    app.state.catalog_client = catalog
    app.state.order_client = orders
    app.state.rating_publisher = publisher

    async def publish_rating_directly(product_id, average_rating, review_count):
        publisher.publish(product_id, average_rating, review_count)

    monkeypatch.setattr(review_app, "_publish_rating", publish_rating_directly)
    app.dependency_overrides[authenticated_customer] = lambda: CustomerIdentity(
        user_id="customer-1", token="customer-jwt"
    )
    yield SimpleNamespace(repository=repository, catalog=catalog, orders=orders, publisher=publisher)
    app.dependency_overrides.clear()
    for attr in ("review_repository", "catalog_client", "order_client", "rating_publisher"):
        delattr(app.state, attr)


async def request(method, path, **kwargs):
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        return await client.request(method, path, **kwargs)


def create_request():
    return CreateReviewRequest(
        order_id="order-1", rating=5, title="Great", body="Works well"
    )


@pytest.mark.asyncio
async def test_list_reviews_returns_paginated_content_and_rating_summary(clients):
    response = await request("GET", "/api/products/product-1/reviews?page=1&size=5")

    assert response.status_code == 200
    assert response.json()["content"][0]["product_id"] == "product-1"
    assert response.json()["average_rating"] == 5.0
    assert response.json()["review_count"] == 1
    assert response.json()["total_pages"] == 1
    assert clients.repository.page_call == ("product-1", 1, 5)


@pytest.mark.asyncio
async def test_create_review_checks_product_order_owner_and_publishes_rating(clients):
    response = await review_app.create_product_review(
        "product-1", create_request(), CustomerIdentity("customer-1", "customer-jwt")
    )

    assert response.verified_purchase is True
    assert clients.catalog.calls == ["product-1"]
    assert clients.orders.calls == [{
        "order_id": "order-1", "user_id": "customer-1", "product_id": "product-1", "token": "customer-jwt"
    }]
    assert clients.repository.create_calls[0]["user_id"] == "customer-1"
    assert clients.publisher.calls == [("product-1", 5.0, 1)]


@pytest.mark.asyncio
async def test_create_review_propagates_catalog_not_found_without_creating(clients):
    clients.catalog.error = HTTPException(status_code=404, detail="Product not found")

    with pytest.raises(HTTPException) as error:
        await review_app.create_product_review(
            "missing", create_request(), CustomerIdentity("customer-1", "customer-jwt")
        )

    assert error.value.status_code == 404
    assert clients.orders.calls == []
    assert clients.repository.create_calls == []


@pytest.mark.asyncio
async def test_create_review_rejects_unverified_purchase(clients):
    clients.orders.error = HTTPException(status_code=403, detail="Confirmed purchase required")

    with pytest.raises(HTTPException) as error:
        await review_app.create_product_review(
            "product-1", create_request(), CustomerIdentity("customer-1", "customer-jwt")
        )

    assert error.value.status_code == 403
    assert clients.repository.create_calls == []


@pytest.mark.asyncio
async def test_create_review_maps_duplicate_review_to_conflict(clients):
    clients.repository.create_error = DuplicateKeyError("duplicate")

    with pytest.raises(HTTPException) as error:
        await review_app.create_product_review(
            "product-1", create_request(), CustomerIdentity("customer-1", "customer-jwt")
        )

    assert error.value.status_code == 409


@pytest.mark.asyncio
async def test_review_mutations_require_customer_authentication():
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        response = await client.post(
            "/api/products/product-1/reviews",
            json={"order_id": "order-1", "rating": 5, "title": "Great", "body": "Works"},
        )

    assert response.status_code == 401


@pytest.mark.asyncio
async def test_update_and_delete_mutate_only_calling_customers_review(clients):
    updated = await review_app.update_my_review(
        "product-1", UpdateReviewRequest(rating=4, title="Good", body="Still works"),
        CustomerIdentity("customer-1", "customer-jwt"),
    )
    deleted = await review_app.delete_my_review(
        "product-1", CustomerIdentity("customer-1", "customer-jwt")
    )

    assert updated.rating == 4
    assert clients.repository.update_calls[0]["user_id"] == "customer-1"
    assert clients.publisher.calls[-1] == ("product-1", 5.0, 1)
    assert deleted.status_code == 204
    assert clients.repository.delete_calls == [("customer-1", "product-1")]


@pytest.mark.asyncio
async def test_update_and_delete_return_404_when_review_missing(clients):
    clients.repository.updated = None
    clients.repository.deleted = False

    with pytest.raises(HTTPException) as update_error:
        await review_app.update_my_review(
            "product-1", UpdateReviewRequest(rating=4, title="Good", body="Still works"),
            CustomerIdentity("customer-1", "customer-jwt"),
        )
    with pytest.raises(HTTPException) as delete_error:
        await review_app.delete_my_review(
            "product-1", CustomerIdentity("customer-1", "customer-jwt")
        )

    assert update_error.value.status_code == 404
    assert delete_error.value.status_code == 404


```

---

## File: `services/search-service/app/config.py`

```python
import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    elasticsearch_url: str = os.getenv("ELASTICSEARCH_URL", "http://localhost:9200")
    elasticsearch_index: str = os.getenv("ELASTICSEARCH_INDEX", "products")
    kafka_bootstrap_servers: str = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    kafka_topic: str = os.getenv("KAFKA_CATALOG_TOPIC", "catalog-events")
    kafka_review_topic: str = os.getenv("KAFKA_REVIEW_TOPIC", "review-events")
    kafka_group_id: str = os.getenv("KAFKA_CONSUMER_GROUP_ID", "search-service-group")
    kafka_enabled: bool = os.getenv("KAFKA_ENABLED", "true").strip().lower() in {
        "1", "true", "yes", "on"
    }
    kafka_retry_seconds: float = float(os.getenv("KAFKA_RETRY_SECONDS", "5"))
    max_page_size: int = int(os.getenv("SEARCH_MAX_PAGE_SIZE", "100"))


settings = Settings()

```

---

## File: `services/search-service/app/__init__.py`

```python


```

---

## File: `services/search-service/app/kafka_runtime.py`

```python
import json
import logging
import threading

from confluent_kafka import Consumer, KafkaError, TopicPartition
from pydantic import ValidationError

from .config import settings
from .schemas import ProductEvent, ReviewRatingEvent
from .search_index import SearchIndex

logger = logging.getLogger(__name__)


class KafkaRuntime:
    def __init__(self, search_index: SearchIndex):
        self.search_index = search_index
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None
        self._running = False

    @property
    def running(self) -> bool:
        return self._running and self._thread is not None and self._thread.is_alive()

    def start(self) -> None:
        if not settings.kafka_enabled:
            logger.info("Kafka consumer disabled")
            return
        self._stop.clear()
        self._thread = threading.Thread(target=self._consume, name="catalog-event-consumer", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        if self._thread and self._thread.is_alive():
            self._thread.join(timeout=10)

    def _consume(self) -> None:
        consumer = Consumer({
            "bootstrap.servers": settings.kafka_bootstrap_servers,
            "group.id": settings.kafka_group_id,
            "enable.auto.commit": False,
            "auto.offset.reset": "earliest",
        })
        consumer.subscribe([settings.kafka_topic, settings.kafka_review_topic])
        self._running = True
        try:
            while not self._stop.is_set():
                message = consumer.poll(1.0)
                if message is None:
                    continue
                if message.error():
                    if message.error().code() != KafkaError._PARTITION_EOF:
                        logger.error("Kafka consume error: %s", message.error())
                    continue
                try:
                    raw_event = json.loads(message.value())
                    if not isinstance(raw_event, dict):
                        raise ValueError("event payload must be a JSON object")
                    event_type = raw_event.get("eventType", raw_event.get("event_type"))
                    if event_type == "ReviewRatingUpdated":
                        event = ReviewRatingEvent.model_validate(raw_event)
                    else:
                        event = ProductEvent.model_validate(raw_event)
                except (json.JSONDecodeError, UnicodeDecodeError, TypeError, ValueError, ValidationError):
                    logger.exception(
                        "Ignoring invalid catalog event at %s[%s] offset %s",
                        message.topic(), message.partition(), message.offset(),
                    )
                    consumer.commit(message=message, asynchronous=False)
                    continue

                if isinstance(event, ReviewRatingEvent):
                    handler = self.search_index.apply_rating_event
                elif event.event_type in {"ProductCreated", "ProductUpdated", "ProductDeleted"}:
                    handler = self.search_index.apply_event
                else:
                    consumer.commit(message=message, asynchronous=False)
                    continue

                try:
                    handler(event)
                except Exception:
                    logger.exception("Could not apply catalog event; retrying Kafka message")
                    consumer.seek(TopicPartition(message.topic(), message.partition(), message.offset()))
                    self._stop.wait(settings.kafka_retry_seconds)
                else:
                    consumer.commit(message=message, asynchronous=False)
        finally:
            self._running = False
            consumer.close()

```

---

## File: `services/search-service/app/main.py`

```python
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

```

---

## File: `services/search-service/app/schemas.py`

```python
from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class ProductSnapshot(BaseModel):
    model_config = ConfigDict(extra="ignore")

    name: str
    description: str | None = None
    category: str
    brand: str
    price: float
    attributes: dict[str, str] | None = None
    rating: float | None = None


class ProductEvent(BaseModel):
    model_config = ConfigDict(extra="ignore", populate_by_name=True)

    event_id: str = Field(alias="eventId")
    event_type: str = Field(alias="eventType")
    product_id: str = Field(alias="productId")
    product: ProductSnapshot | None = None


class ReviewRatingEvent(BaseModel):
    model_config = ConfigDict(extra="ignore", populate_by_name=True)

    event_id: str = Field(alias="eventId")
    event_type: str = Field(alias="eventType")
    product_id: str = Field(alias="productId")
    average_rating: float | None = Field(default=None, alias="averageRating", ge=0, le=5)
    review_count: int = Field(alias="reviewCount", ge=0)


class FacetValue(BaseModel):
    value: str
    count: int


class PriceFacet(BaseModel):
    key: str
    from_price: float | None = Field(default=None, alias="from")
    to_price: float | None = Field(default=None, alias="to")
    count: int


class SearchFacets(BaseModel):
    categories: list[FacetValue] = Field(default_factory=list)
    brands: list[FacetValue] = Field(default_factory=list)
    prices: list[PriceFacet] = Field(default_factory=list)
    ratings: list[FacetValue] = Field(default_factory=list)


class SearchResponse(BaseModel):
    content: list[dict[str, Any]]
    page: int
    size: int
    total_elements: int = Field(alias="totalElements")
    total_pages: int = Field(alias="totalPages")
    facets: SearchFacets

```

---

## File: `services/search-service/app/search_index.py`

```python
from typing import Any

from elasticsearch import Elasticsearch, NotFoundError

from .config import settings
from .schemas import ProductEvent


class SearchIndex:
    def __init__(self, client: Elasticsearch, alias: str = settings.elasticsearch_index):
        self.client = client
        self.alias = alias

    def ensure_index(self) -> None:
        if self.client.indices.exists_alias(name=self.alias):
            return
        self.client.indices.create(
            index=f"{self.alias}-v1",
            aliases={self.alias: {}},
            settings={"number_of_shards": 1, "number_of_replicas": 0},
            mappings={
                "properties": {
                    "productId": {"type": "keyword"},
                    "name": {
                        "type": "text",
                        "fields": {"keyword": {"type": "keyword", "ignore_above": 256}},
                    },
                    "description": {"type": "text"},
                    "attributeText": {"type": "text"},
                    "category": {"type": "keyword"},
                    "brand": {"type": "keyword"},
                    "price": {"type": "scaled_float", "scaling_factor": 100},
                    "attributes": {"type": "flattened"},
                    "rating": {"type": "float"},
                    "reviewCount": {"type": "integer"},
                }
            },
        )

    def apply_event(self, event: ProductEvent) -> None:
        if event.event_type == "ProductDeleted":
            try:
                self.client.delete(index=self.alias, id=event.product_id, refresh="wait_for")
            except NotFoundError:
                pass
            return

        if event.event_type not in {"ProductCreated", "ProductUpdated"}:
            return
        if event.product is None:
            raise ValueError(f"{event.event_type} event has no product snapshot")

        product = event.product.model_dump(exclude_none=True)
        attributes = product.get("attributes") or {}
        product["attributes"] = attributes
        product["attributeText"] = " ".join(
            f"{key} {value}" for key, value in attributes.items()
        )
        product["productId"] = event.product_id
        self.client.update(
            index=self.alias,
            id=event.product_id,
            doc=product,
            doc_as_upsert=True,
            refresh="wait_for",
        )

    def apply_rating_event(self, event: Any) -> None:
        if event.event_type != "ReviewRatingUpdated":
            return
        self.client.update(
            index=self.alias,
            id=event.product_id,
            doc={"rating": event.average_rating, "reviewCount": event.review_count},
            doc_as_upsert=True,
            refresh="wait_for",
        )

    def search(
        self,
        *,
        query: str | None,
        category: str | None,
        brand: str | None,
        min_price: float | None,
        max_price: float | None,
        min_rating: float | None,
        page: int,
        size: int,
        sort: str,
    ) -> dict[str, Any]:
        text_query: dict[str, Any] = (
            {"multi_match": {
                "query": query,
                "fields": ["name^3", "brand^2", "category^2", "description", "attributeText"],
                "type": "best_fields",
                "fuzziness": "AUTO",
            }}
            if query
            else {"match_all": {}}
        )

        filters: list[dict[str, Any]] = []
        if category:
            filters.append({"term": {"category": {"value": category, "case_insensitive": True}}})
        if brand:
            filters.append({"term": {"brand": {"value": brand, "case_insensitive": True}}})
        price_range: dict[str, float] = {}
        if min_price is not None:
            price_range["gte"] = min_price
        if max_price is not None:
            price_range["lte"] = max_price
        if price_range:
            filters.append({"range": {"price": price_range}})
        if min_rating is not None:
            filters.append({"range": {"rating": {"gte": min_rating}}})

        sort_options: dict[str, Any] = {
            "relevance": [{"_score": "desc"}, {"name.keyword": "asc"}],
            "price_asc": [{"price": "asc"}, {"name.keyword": "asc"}],
            "price_desc": [{"price": "desc"}, {"name.keyword": "asc"}],
            "name_asc": [{"name.keyword": "asc"}],
            "rating_desc": [{"rating": {"order": "desc", "missing": "_last"}}, {"name.keyword": "asc"}],
        }
        query_body: dict[str, Any] = {
            "bool": {"must": [text_query], "filter": filters}
        }
        result = self.client.search(
            index=self.alias,
            from_=page * size,
            size=size,
            query=query_body,
            sort=sort_options[sort],
            aggs={
                "categories": {"terms": {"field": "category", "size": 30}},
                "brands": {"terms": {"field": "brand", "size": 30}},
                "prices": {
                    "range": {
                        "field": "price",
                        "ranges": [
                            {"key": "under_25", "to": 25},
                            {"key": "25_to_50", "from": 25, "to": 50},
                            {"key": "50_to_100", "from": 50, "to": 100},
                            {"key": "100_to_250", "from": 100, "to": 250},
                            {"key": "250_plus", "from": 250},
                        ],
                    }
                },
                "ratings": {"terms": {"field": "rating", "size": 5, "order": {"_key": "desc"}}},
            },
        )
        hits = result["hits"]["hits"]
        total_value = result["hits"]["total"]
        total = total_value["value"] if isinstance(total_value, dict) else total_value
        pages = (total + size - 1) // size if total else 0
        aggregations = result.get("aggregations", {})
        return {
            "content": [{**hit["_source"], "score": hit.get("_score")} for hit in hits],
            "page": page,
            "size": size,
            "totalElements": total,
            "totalPages": pages,
            "facets": {
                "categories": [
                    {"value": bucket["key"], "count": bucket["doc_count"]}
                    for bucket in aggregations.get("categories", {}).get("buckets", [])
                ],
                "brands": [
                    {"value": bucket["key"], "count": bucket["doc_count"]}
                    for bucket in aggregations.get("brands", {}).get("buckets", [])
                ],
                "prices": [
                    {
                        "key": bucket["key"],
                        "from": bucket.get("from"),
                        "to": bucket.get("to"),
                        "count": bucket["doc_count"],
                    }
                    for bucket in aggregations.get("prices", {}).get("buckets", [])
                ],
                "ratings": [
                    {"value": str(bucket["key"]), "count": bucket["doc_count"]}
                    for bucket in aggregations.get("ratings", {}).get("buckets", [])
                ],
            },
        }

```

---

## File: `services/search-service/.pytest_cache/README.md`

```markdown
# pytest cache directory #

This directory contains data from the pytest's cache plugin,
which provides the `--lf` and `--ff` options, as well as the `cache` fixture.

**Do not** commit this to version control.

See [the docs](https://docs.pytest.org/en/stable/how-to/cache.html) for more information.

```

---

## File: `services/search-service/README.md`

```markdown
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

```

---

## File: `services/search-service/tests/test_search_index.py`

```python
from typing import Any

from app.schemas import ProductEvent
from app.search_index import SearchIndex


def event(event_type: str, *, attributes: dict[str, str] | None = None) -> ProductEvent:
    payload: dict[str, Any] = {
        "eventId": "event-1",
        "eventType": event_type,
        "productId": "product-1",
    }
    if event_type != "ProductDeleted":
        payload["product"] = {
            "name": "Trail Runner",
            "description": "Lightweight running shoe",
            "category": "Footwear",
            "brand": "Acme",
            "price": 89.99,
            "attributes": attributes,
        }
    return ProductEvent.model_validate(payload)


class FakeIndices:
    def __init__(self, has_alias: bool = False):
        self.has_alias = has_alias
        self.create_calls: list[dict[str, Any]] = []

    def exists_alias(self, *, name: str) -> bool:
        return self.has_alias

    def create(self, **kwargs: Any) -> None:
        self.create_calls.append(kwargs)
        self.has_alias = True


class FakeElasticsearch:
    def __init__(self, search_response: dict[str, Any] | None = None):
        self.indices = FakeIndices()
        self.index_calls: list[dict[str, Any]] = []
        self.update_calls: list[dict[str, Any]] = []
        self.delete_calls: list[dict[str, Any]] = []
        self.search_calls: list[dict[str, Any]] = []
        self.search_response = search_response or {"hits": {"hits": [], "total": {"value": 0}}}

    def index(self, **kwargs: Any) -> None:
        self.index_calls.append(kwargs)

    def update(self, **kwargs: Any) -> None:
        self.update_calls.append(kwargs)

    def delete(self, **kwargs: Any) -> None:
        self.delete_calls.append(kwargs)

    def search(self, **kwargs: Any) -> dict[str, Any]:
        self.search_calls.append(kwargs)
        return self.search_response


def test_ensure_index_creates_alias_and_product_mapping():
    client = FakeElasticsearch()
    search = SearchIndex(client, alias="products")

    search.ensure_index()
    search.ensure_index()

    assert len(client.indices.create_calls) == 1
    created = client.indices.create_calls[0]
    assert created["index"] == "products-v1"
    assert created["aliases"] == {"products": {}}
    assert created["mappings"]["properties"]["category"]["type"] == "keyword"
    assert created["mappings"]["properties"]["price"]["type"] == "scaled_float"


def test_created_and_updated_events_index_by_product_id():
    client = FakeElasticsearch()
    search = SearchIndex(client, alias="products")

    search.apply_event(event("ProductCreated", attributes={"color": "blue"}))
    search.apply_event(event("ProductUpdated", attributes={"color": "red"}))

    assert [call["id"] for call in client.update_calls] == ["product-1", "product-1"]
    assert client.update_calls[0]["doc"]["productId"] == "product-1"
    assert client.update_calls[0]["doc"]["attributeText"] == "color blue"
    assert client.update_calls[1]["doc"]["attributeText"] == "color red"
    assert all(call["index"] == "products" for call in client.update_calls)
    assert all(call["doc_as_upsert"] is True for call in client.update_calls)


def test_missing_attributes_are_indexed_as_empty():
    client = FakeElasticsearch()
    search = SearchIndex(client, alias="products")

    search.apply_event(event("ProductCreated"))

    assert client.update_calls[0]["doc"]["attributes"] == {}
    assert client.update_calls[0]["doc"]["attributeText"] == ""


def test_deleted_event_removes_product_document():
    client = FakeElasticsearch()
    search = SearchIndex(client, alias="products")

    search.apply_event(event("ProductDeleted"))

    assert client.delete_calls == [{"index": "products", "id": "product-1", "refresh": "wait_for"}]
    assert client.index_calls == []
    assert client.update_calls == []


def test_unknown_event_type_is_ignored():
    client = FakeElasticsearch()
    search = SearchIndex(client, alias="products")

    search.apply_event(event("ProductArchived"))

    assert client.index_calls == []
    assert client.update_calls == []
    assert client.delete_calls == []


def test_search_builds_query_filters_and_returns_pagination_and_facets():
    response = {
        "hits": {
            "hits": [{"_source": {"productId": "product-1", "name": "Trail Runner"}, "_score": 4.2}],
            "total": {"value": 21, "relation": "eq"},
        },
        "aggregations": {
            "categories": {"buckets": [{"key": "Footwear", "doc_count": 12}]},
            "brands": {"buckets": [{"key": "Acme", "doc_count": 7}]},
            "prices": {"buckets": [{"key": "50_to_100", "from": 50.0, "to": 100.0, "doc_count": 5}]},
            "ratings": {"buckets": [{"key": 4.5, "doc_count": 3}]},
        },
    }
    client = FakeElasticsearch(search_response=response)
    search = SearchIndex(client, alias="products")

    result = search.search(
        query="trail runner",
        category="Footwear",
        brand="Acme",
        min_price=40,
        max_price=120,
        min_rating=4,
        page=2,
        size=10,
        sort="price_asc",
    )

    request = client.search_calls[0]
    assert request["from_"] == 20
    assert request["size"] == 10
    assert request["sort"] == [{"price": "asc"}, {"name.keyword": "asc"}]
    filters = request["query"]["bool"]["filter"]
    assert {"term": {"category": {"value": "Footwear", "case_insensitive": True}}} in filters
    assert {"term": {"brand": {"value": "Acme", "case_insensitive": True}}} in filters
    assert {"range": {"price": {"gte": 40, "lte": 120}}} in filters
    assert {"range": {"rating": {"gte": 4}}} in filters
    assert result["totalElements"] == 21
    assert result["totalPages"] == 3
    assert result["content"][0]["score"] == 4.2
    assert result["facets"]["categories"] == [{"value": "Footwear", "count": 12}]
    assert result["facets"]["brands"] == [{"value": "Acme", "count": 7}]
    assert result["facets"]["prices"][0]["key"] == "50_to_100"
    assert result["facets"]["ratings"] == [{"value": "4.5", "count": 3}]


def test_empty_query_uses_match_all_and_zero_results_have_zero_pages():
    client = FakeElasticsearch({"hits": {"hits": [], "total": {"value": 0}}})
    search = SearchIndex(client, alias="products")

    result = search.search(
        query=None,
        category=None,
        brand=None,
        min_price=None,
        max_price=None,
        min_rating=None,
        page=0,
        size=20,
        sort="relevance",
    )

    assert client.search_calls[0]["query"]["bool"]["must"] == [{"match_all": {}}]
    assert result["totalElements"] == 0
    assert result["totalPages"] == 0
    assert result["content"] == []

```

---

## File: `services/wishlist-service/app/auth.py`

```python
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from jwt import InvalidTokenError
import jwt

from .config import settings

if len(settings.app_jwt_secret.encode("utf-8")) < 32:
    raise RuntimeError("APP_JWT_SECRET must contain at least 32 bytes")

bearer_scheme = HTTPBearer(auto_error=False)


async def authenticated_user_id(
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer_scheme),
) -> str:
    if credentials is None or credentials.scheme.lower() != "bearer":
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Bearer token required",
            headers={"WWW-Authenticate": "Bearer"},
        )

    try:
        claims = jwt.decode(credentials.credentials, settings.app_jwt_secret, algorithms=["HS256"])
    except InvalidTokenError as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid bearer token",
            headers={"WWW-Authenticate": "Bearer"},
        ) from exc

    subject = claims.get("sub")
    if not isinstance(subject, str) or not subject.strip():
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Bearer token has no user subject",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return subject

```

---

## File: `services/wishlist-service/app/catalog_client.py`

```python
from urllib.parse import quote

import httpx
from fastapi import HTTPException, status
from pydantic import BaseModel, Field, ValidationError

from .config import settings


class CatalogProduct(BaseModel):
    id: str
    name: str = Field(min_length=1)


class CatalogClient:
    def __init__(
        self,
        base_url: str = settings.catalog_service_url,
        client: httpx.AsyncClient | None = None,
    ):
        self.base_url = base_url.rstrip("/")
        self.client = client or httpx.AsyncClient(timeout=httpx.Timeout(3.0, connect=1.0))

    async def ensure_product_exists(self, product_id: str) -> None:
        try:
            response = await self.client.get(
                f"{self.base_url}/api/products/{quote(product_id, safe='')}"
            )
        except httpx.RequestError as exc:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Catalog service is unavailable",
            ) from exc

        if response.status_code == status.HTTP_404_NOT_FOUND:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Product not found")
        if response.status_code >= 500:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Catalog service is unavailable",
            )
        if response.status_code != status.HTTP_200_OK:
            raise HTTPException(
                status_code=status.HTTP_502_BAD_GATEWAY,
                detail="Catalog service returned an unexpected response",
            )

        try:
            product = CatalogProduct.model_validate(response.json())
        except (ValueError, ValidationError) as exc:
            raise HTTPException(
                status_code=status.HTTP_502_BAD_GATEWAY,
                detail="Catalog service returned invalid product data",
            ) from exc
        if product.id != product_id:
            raise HTTPException(
                status_code=status.HTTP_502_BAD_GATEWAY,
                detail="Catalog service returned a mismatched product",
            )

    async def close(self) -> None:
        await self.client.aclose()

```

---

## File: `services/wishlist-service/app/config.py`

```python
import os
from dataclasses import dataclass

from dotenv import load_dotenv

load_dotenv()


@dataclass(frozen=True)
class Settings:
    mongodb_uri: str = os.getenv(
        "MONGODB_URI",
        "mongodb://admin:mongo_dev_password@localhost:27017/wishlist_db?authSource=admin&directConnection=true",
    )
    catalog_service_url: str = os.getenv("CATALOG_SERVICE_URL", "http://localhost:8081").rstrip("/")
    app_jwt_secret: str = os.getenv(
        "APP_JWT_SECRET",
        "local-development-secret-change-this-to-a-long-random-value",
    )
    max_page_size: int = int(os.getenv("WISHLIST_MAX_PAGE_SIZE", "100"))


settings = Settings()

```

---

## File: `services/wishlist-service/app/__init__.py`

```python


```

---

## File: `services/wishlist-service/app/main.py`

```python
from contextlib import asynccontextmanager
from typing import Annotated

from fastapi import Depends, FastAPI, HTTPException, Query, Response, status
from pymongo import AsyncMongoClient
from pymongo.errors import PyMongoError

from .auth import authenticated_user_id
from .catalog_client import CatalogClient
from .config import settings
from .models import AddWishlistItemRequest, WishlistItemResponse, WishlistResponse
from .repository import WishlistRepository


@asynccontextmanager
async def lifespan(app: FastAPI):
    client = AsyncMongoClient(settings.mongodb_uri, serverSelectionTimeoutMS=5000, tz_aware=True)
    database = client.get_default_database()
    if database is None:
        raise RuntimeError("MONGODB_URI must include a database name")
    await client.admin.command("ping")
    repository = WishlistRepository(database.get_collection("wishlist_items"))
    await repository.ensure_indexes()
    catalog_client = CatalogClient()
    app.state.mongo_client = client
    app.state.wishlist_repository = repository
    app.state.catalog_client = catalog_client
    try:
        yield
    finally:
        await catalog_client.close()
        await client.close()


app = FastAPI(title="Wishlist Service", version="1.0.0", lifespan=lifespan)


@app.get("/health")
async def health() -> dict[str, str]:
    client: AsyncMongoClient | None = getattr(app.state, "mongo_client", None)
    try:
        if client is None:
            raise PyMongoError("MongoDB client is not initialized")
        await client.admin.command("ping")
    except PyMongoError as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="MongoDB is unavailable",
        ) from exc
    return {"status": "UP"}


@app.get("/api/wishlist", response_model=WishlistResponse)
async def get_wishlist(
    user_id: Annotated[str, Depends(authenticated_user_id)],
    page: int = Query(default=0, ge=0),
    size: int = Query(default=20, ge=1, le=settings.max_page_size),
) -> WishlistResponse:
    repository: WishlistRepository = app.state.wishlist_repository
    items, total = await repository.list_items(user_id, page, size)
    return WishlistResponse(
        user_id=user_id,
        items=[WishlistItemResponse(product_id=item["productId"], added_at=item["addedAt"]) for item in items],
        page=page,
        size=size,
        total_elements=total,
        total_pages=repository.total_pages(total, size),
    )


@app.put("/api/wishlist/items/{product_id}", response_model=WishlistItemResponse)
async def add_to_wishlist(
    product_id: str,
    user_id: Annotated[str, Depends(authenticated_user_id)],
) -> WishlistItemResponse:
    if not product_id.strip() or len(product_id) > 200:
        raise HTTPException(status_code=422, detail="product_id must contain 1 to 200 characters")
    product_id = product_id.strip()
    catalog_client: CatalogClient = app.state.catalog_client
    await catalog_client.ensure_product_exists(product_id)
    repository: WishlistRepository = app.state.wishlist_repository
    item = await repository.add_item(user_id, product_id)
    return WishlistItemResponse(product_id=item["productId"], added_at=item["addedAt"])


@app.delete("/api/wishlist/items/{product_id}", status_code=status.HTTP_204_NO_CONTENT)
async def remove_from_wishlist(
    product_id: str,
    user_id: Annotated[str, Depends(authenticated_user_id)],
) -> Response:
    repository: WishlistRepository = app.state.wishlist_repository
    await repository.remove_item(user_id, product_id)
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@app.delete("/api/wishlist", status_code=status.HTTP_204_NO_CONTENT)
async def clear_wishlist(
    user_id: Annotated[str, Depends(authenticated_user_id)],
) -> Response:
    repository: WishlistRepository = app.state.wishlist_repository
    await repository.clear(user_id)
    return Response(status_code=status.HTTP_204_NO_CONTENT)

```

---

## File: `services/wishlist-service/app/models.py`

```python
from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field, field_validator


class AddWishlistItemRequest(BaseModel):
    product_id: str = Field(min_length=1, max_length=200)

    @field_validator("product_id")
    @classmethod
    def trim_product_id(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise ValueError("product_id must not be blank")
        return normalized


class WishlistItemResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    product_id: str
    added_at: datetime


class WishlistResponse(BaseModel):
    user_id: str
    items: list[WishlistItemResponse] = Field(default_factory=list)
    page: int
    size: int
    total_elements: int
    total_pages: int

```

---

## File: `services/wishlist-service/app/repository.py`

```python
from datetime import datetime, timezone
from math import ceil
from typing import Any

from pymongo import ASCENDING, DESCENDING, ReturnDocument
from pymongo.errors import DuplicateKeyError


class WishlistRepository:
    def __init__(self, collection: Any):
        self.collection = collection

    async def ensure_indexes(self) -> None:
        await self.collection.create_index(
            [("userId", ASCENDING), ("productId", ASCENDING)],
            unique=True,
            name="uq_wishlist_user_product",
        )
        await self.collection.create_index(
            [("userId", ASCENDING), ("addedAt", DESCENDING)],
            name="ix_wishlist_user_added_at",
        )

    async def add_item(self, user_id: str, product_id: str) -> dict[str, Any]:
        try:
            item = await self.collection.find_one_and_update(
                {"userId": user_id, "productId": product_id},
                {"$setOnInsert": {
                    "userId": user_id,
                    "productId": product_id,
                    "addedAt": datetime.now(timezone.utc),
                }},
                upsert=True,
                return_document=ReturnDocument.AFTER,
            )
        except DuplicateKeyError:
            # Another request may insert this same product after the upsert check.
            item = await self.collection.find_one({"userId": user_id, "productId": product_id})
            if item is None:
                raise
        return item

    async def list_items(self, user_id: str, page: int, size: int) -> tuple[list[dict[str, Any]], int]:
        total = await self.collection.count_documents({"userId": user_id})
        cursor = (
            self.collection.find({"userId": user_id})
            .sort("addedAt", DESCENDING)
            .skip(page * size)
            .limit(size)
        )
        items = await cursor.to_list(length=size)
        return items, total

    async def remove_item(self, user_id: str, product_id: str) -> None:
        await self.collection.delete_one({"userId": user_id, "productId": product_id})

    async def clear(self, user_id: str) -> None:
        await self.collection.delete_many({"userId": user_id})

    @staticmethod
    def total_pages(total: int, size: int) -> int:
        return ceil(total / size) if total else 0

```

---

## File: `services/wishlist-service/.pytest_cache/README.md`

```markdown
# pytest cache directory #

This directory contains data from the pytest's cache plugin,
which provides the `--lf` and `--ff` options, as well as the `cache` fixture.

**Do not** commit this to version control.

See [the docs](https://docs.pytest.org/en/stable/how-to/cache.html) for more information.

```

---

## File: `services/wishlist-service/pytest.ini`

```properties
[pytest]
asyncio_mode = auto

```

---

## File: `services/wishlist-service/README.md`

```markdown
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

```

---

## File: `services/wishlist-service/tests/test_auth.py`

```python
import jwt
import pytest
from fastapi import HTTPException
from fastapi.security import HTTPAuthorizationCredentials

from app.auth import authenticated_user_id
from app.config import settings


@pytest.mark.asyncio
async def test_authenticated_user_id_returns_verified_subject():
    token = jwt.encode({"sub": "customer-123"}, settings.app_jwt_secret, algorithm="HS256")

    user_id = await authenticated_user_id(
        HTTPAuthorizationCredentials(scheme="Bearer", credentials=token)
    )

    assert user_id == "customer-123"


@pytest.mark.asyncio
async def test_authenticated_user_id_rejects_missing_credentials():
    with pytest.raises(HTTPException) as error:
        await authenticated_user_id(None)

    assert error.value.status_code == 401
    assert error.value.headers["WWW-Authenticate"] == "Bearer"


@pytest.mark.asyncio
async def test_authenticated_user_id_rejects_invalid_signature():
    token = jwt.encode({"sub": "customer-123"}, "wrong-secret-that-is-long-enough-for-hs256", algorithm="HS256")

    with pytest.raises(HTTPException) as error:
        await authenticated_user_id(
            HTTPAuthorizationCredentials(scheme="Bearer", credentials=token)
        )

    assert error.value.status_code == 401


@pytest.mark.asyncio
async def test_authenticated_user_id_rejects_missing_subject():
    token = jwt.encode({"email": "customer@example.com"}, settings.app_jwt_secret, algorithm="HS256")

    with pytest.raises(HTTPException) as error:
        await authenticated_user_id(
            HTTPAuthorizationCredentials(scheme="Bearer", credentials=token)
        )

    assert error.value.status_code == 401

```

---

## File: `services/wishlist-service/tests/test_catalog_client.py`

```python
import httpx
import pytest
from fastapi import HTTPException

from app.catalog_client import CatalogClient


def client_for(response: httpx.Response | Exception) -> CatalogClient:
    def handler(request: httpx.Request) -> httpx.Response:
        if isinstance(response, Exception):
            raise response
        return response

    http_client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    return CatalogClient("http://catalog.test/", client=http_client)


@pytest.mark.asyncio
async def test_ensure_product_exists_requests_encoded_product_id_and_accepts_product():
    requests: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        return httpx.Response(200, json={"id": "item/1", "name": "Product", "price": 25})

    catalog = CatalogClient(
        "http://catalog.test",
        client=httpx.AsyncClient(transport=httpx.MockTransport(handler)),
    )
    try:
        await catalog.ensure_product_exists("item/1")
    finally:
        await catalog.close()

    assert requests[0].url.raw_path == b"/api/products/item%2F1"


@pytest.mark.asyncio
async def test_ensure_product_exists_maps_not_found_to_404():
    catalog = client_for(httpx.Response(404))
    try:
        with pytest.raises(HTTPException) as error:
            await catalog.ensure_product_exists("missing")
    finally:
        await catalog.close()

    assert error.value.status_code == 404


@pytest.mark.asyncio
async def test_ensure_product_exists_maps_catalog_failure_to_503():
    catalog = client_for(httpx.Response(503))
    try:
        with pytest.raises(HTTPException) as error:
            await catalog.ensure_product_exists("product-1")
    finally:
        await catalog.close()

    assert error.value.status_code == 503


@pytest.mark.asyncio
async def test_ensure_product_exists_rejects_mismatched_product_id():
    catalog = client_for(httpx.Response(200, json={"id": "different", "name": "Product"}))
    try:
        with pytest.raises(HTTPException) as error:
            await catalog.ensure_product_exists("product-1")
    finally:
        await catalog.close()

    assert error.value.status_code == 502


@pytest.mark.asyncio
async def test_ensure_product_exists_maps_network_failure_to_503():
    failure = httpx.ConnectError("connection refused", request=httpx.Request("GET", "http://catalog.test"))
    catalog = client_for(failure)
    try:
        with pytest.raises(HTTPException) as error:
            await catalog.ensure_product_exists("product-1")
    finally:
        await catalog.close()

    assert error.value.status_code == 503

```

---

## File: `services/wishlist-service/tests/test_repository.py`

```python
from datetime import datetime, timezone

import pytest
from pymongo import ASCENDING, DESCENDING, ReturnDocument
from pymongo.errors import DuplicateKeyError

from app.repository import WishlistRepository


class FakeCursor:
    def __init__(self, documents):
        self.documents = documents
        self.sort_args = None
        self.skip_count = None
        self.limit_count = None

    def sort(self, *args):
        self.sort_args = args
        return self

    def skip(self, count):
        self.skip_count = count
        return self

    def limit(self, count):
        self.limit_count = count
        return self

    async def to_list(self, length):
        return self.documents[:length]


class FakeCollection:
    def __init__(self):
        self.index_calls = []
        self.update_calls = []
        self.documents = []
        self.update_result = None
        self.duplicate_on_update = False
        self.find_one_result = None
        self.cursor = FakeCursor([])
        self.total_count = 0
        self.deleted_one = None
        self.deleted_many = None

    async def create_index(self, keys, **kwargs):
        self.index_calls.append((keys, kwargs))

    async def find_one_and_update(self, query, update, **kwargs):
        self.update_calls.append((query, update, kwargs))
        if self.duplicate_on_update:
            raise DuplicateKeyError("duplicate")
        return self.update_result

    async def find_one(self, query):
        self.find_one_query = query
        return self.find_one_result

    async def count_documents(self, query):
        self.count_query = query
        return self.total_count

    def find(self, query):
        self.find_query = query
        return self.cursor

    async def delete_one(self, query):
        self.deleted_one = query

    async def delete_many(self, query):
        self.deleted_many = query


@pytest.mark.asyncio
async def test_ensure_indexes_creates_unique_user_product_and_list_indexes():
    collection = FakeCollection()
    repository = WishlistRepository(collection)

    await repository.ensure_indexes()

    assert collection.index_calls[0][0] == [("userId", ASCENDING), ("productId", ASCENDING)]
    assert collection.index_calls[0][1]["unique"] is True
    assert collection.index_calls[0][1]["name"] == "uq_wishlist_user_product"
    assert collection.index_calls[1][0] == [("userId", ASCENDING), ("addedAt", DESCENDING)]


@pytest.mark.asyncio
async def test_add_item_upserts_without_overwriting_original_added_time():
    added_at = datetime.now(timezone.utc)
    collection = FakeCollection()
    collection.update_result = {"userId": "user-1", "productId": "product-1", "addedAt": added_at}
    repository = WishlistRepository(collection)

    item = await repository.add_item("user-1", "product-1")

    query, update, options = collection.update_calls[0]
    assert query == {"userId": "user-1", "productId": "product-1"}
    assert set(update) == {"$setOnInsert"}
    assert update["$setOnInsert"]["userId"] == "user-1"
    assert update["$setOnInsert"]["productId"] == "product-1"
    assert options["upsert"] is True
    assert options["return_document"] == ReturnDocument.AFTER
    assert item["addedAt"] == added_at


@pytest.mark.asyncio
async def test_add_item_recovers_concurrent_duplicate_upsert():
    existing = {"userId": "user-1", "productId": "product-1", "addedAt": datetime.now(timezone.utc)}
    collection = FakeCollection()
    collection.duplicate_on_update = True
    collection.find_one_result = existing
    repository = WishlistRepository(collection)

    item = await repository.add_item("user-1", "product-1")

    assert item is existing
    assert collection.find_one_query == {"userId": "user-1", "productId": "product-1"}


@pytest.mark.asyncio
async def test_list_items_filters_by_user_and_applies_pagination():
    collection = FakeCollection()
    collection.total_count = 25
    collection.cursor = FakeCursor([{"productId": "product-21"}])
    repository = WishlistRepository(collection)

    items, total = await repository.list_items("user-1", page=2, size=10)

    assert collection.count_query == {"userId": "user-1"}
    assert collection.find_query == {"userId": "user-1"}
    assert collection.cursor.sort_args == ("addedAt", DESCENDING)
    assert collection.cursor.skip_count == 20
    assert collection.cursor.limit_count == 10
    assert items == [{"productId": "product-21"}]
    assert total == 25


@pytest.mark.asyncio
async def test_remove_and_clear_only_target_calling_users_data():
    collection = FakeCollection()
    repository = WishlistRepository(collection)

    await repository.remove_item("user-1", "product-1")
    await repository.clear("user-1")

    assert collection.deleted_one == {"userId": "user-1", "productId": "product-1"}
    assert collection.deleted_many == {"userId": "user-1"}


@pytest.mark.parametrize(
    ("total", "size", "expected"),
    [(0, 20, 0), (1, 20, 1), (20, 20, 1), (21, 20, 2)],
)
def test_total_pages(total, size, expected):
    assert WishlistRepository.total_pages(total, size) == expected

```

---

## File: `services/wishlist-service/tests/test_routes.py`

```python
from datetime import datetime, timezone

import pytest
from fastapi import HTTPException

from app.main import add_to_wishlist, clear_wishlist, get_wishlist, remove_from_wishlist, app


class FakeRepository:
    def __init__(self):
        self.list_result = ([], 0)
        self.add_result = {
            "productId": "product-1",
            "addedAt": datetime(2026, 1, 1, tzinfo=timezone.utc),
        }
        self.list_calls = []
        self.add_calls = []
        self.remove_calls = []
        self.clear_calls = []

    async def list_items(self, user_id, page, size):
        self.list_calls.append((user_id, page, size))
        return self.list_result

    async def add_item(self, user_id, product_id):
        self.add_calls.append((user_id, product_id))
        return self.add_result

    async def remove_item(self, user_id, product_id):
        self.remove_calls.append((user_id, product_id))

    async def clear(self, user_id):
        self.clear_calls.append(user_id)

    @staticmethod
    def total_pages(total, size):
        return (total + size - 1) // size if total else 0


class FakeCatalogClient:
    def __init__(self):
        self.product_ids = []
        self.error = None

    async def ensure_product_exists(self, product_id):
        self.product_ids.append(product_id)
        if self.error:
            raise self.error


def configure_app(repository=None, catalog=None):
    app.state.wishlist_repository = repository or FakeRepository()
    app.state.catalog_client = catalog or FakeCatalogClient()


@pytest.mark.asyncio
async def test_get_wishlist_scopes_query_to_verified_user_and_returns_page():
    repository = FakeRepository()
    repository.list_result = ([{
        "productId": "product-1",
        "addedAt": datetime(2026, 1, 1, tzinfo=timezone.utc),
    }], 1)
    configure_app(repository=repository)
    response = await get_wishlist(user_id="customer-1", page=0, size=5)

    assert response.user_id == "customer-1"
    assert response.items[0].product_id == "product-1"
    assert response.total_elements == 1
    assert repository.list_calls == [("customer-1", 0, 5)]


@pytest.mark.asyncio
async def test_add_wishlist_item_checks_catalog_and_uses_verified_user():
    repository = FakeRepository()
    catalog = FakeCatalogClient()
    configure_app(repository=repository, catalog=catalog)
    response = await add_to_wishlist(product_id="product-1", user_id="customer-1")

    assert response.product_id == "product-1"
    assert catalog.product_ids == ["product-1"]
    assert repository.add_calls == [("customer-1", "product-1")]


@pytest.mark.asyncio
async def test_add_wishlist_item_returns_catalog_not_found():
    catalog = FakeCatalogClient()
    catalog.error = HTTPException(status_code=404, detail="Product not found")
    configure_app(catalog=catalog)

    with pytest.raises(HTTPException) as error:
        await add_to_wishlist(product_id="missing", user_id="customer-1")
    assert error.value.status_code == 404


@pytest.mark.asyncio
async def test_remove_wishlist_item_uses_verified_user_and_returns_no_content():
    repository = FakeRepository()
    configure_app(repository=repository)
    response = await remove_from_wishlist(product_id="product-1", user_id="customer-1")

    assert response.status_code == 204
    assert repository.remove_calls == [("customer-1", "product-1")]


@pytest.mark.asyncio
async def test_clear_wishlist_only_clears_verified_user():
    repository = FakeRepository()
    configure_app(repository=repository)
    response = await clear_wishlist(user_id="customer-1")

    assert response.status_code == 204
    assert repository.clear_calls == ["customer-1"]

```
