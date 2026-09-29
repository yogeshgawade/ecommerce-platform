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

