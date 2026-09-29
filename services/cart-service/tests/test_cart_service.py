import asyncio
from decimal import Decimal
from time import time

import httpx
import jwt
import pytest
from redis.exceptions import WatchError

from app import main, repository
from app.auth import JWT_SECRET
from app.catalog_client import CatalogProduct
from app.models import Cart
from app.redis_client import CART_TTL_SECONDS


class MemoryPipeline:
    def __init__(self, client):
        self.client = client
        self.queued_set = None

    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc_value, traceback):
        return False

    async def watch(self, key):
        self.client.watched_keys.append(key)

    async def get(self, key):
        return self.client.values.get(key)

    def multi(self):
        return None

    def set(self, key, value, ex=None):
        self.queued_set = (key, value, ex)

    async def execute(self):
        if self.client.conflict_next_execute:
            self.client.conflict_next_execute = False
            key, value, ttl = self.queued_set
            concurrent_cart = Cart.model_validate_json(self.client.values.get(key))
            if concurrent_cart.items:
                concurrent_cart.items[0].quantity += 1
                await self.client.set(key, concurrent_cart.model_dump_json(), ex=ttl)
            raise WatchError("simulated concurrent cart update")
        key, value, ttl = self.queued_set
        await self.client.set(key, value, ex=ttl)


class MemoryRedis:
    def __init__(self):
        self.values = {}
        self.expires_at = {}
        self.watched_keys = []
        self.conflict_next_execute = False

    async def ping(self):
        return True

    async def get(self, key):
        return self.values.get(key)

    async def set(self, key, value, ex=None):
        self.values[key] = value
        self.expires_at[key] = time() + ex if ex else None
        return True

    async def delete(self, key):
        self.expires_at.pop(key, None)
        return self.values.pop(key, None) is not None

    async def ttl(self, key):
        if key not in self.values:
            return -2
        expiry = self.expires_at.get(key)
        return -1 if expiry is None else max(0, int(expiry - time()))

    def pipeline(self):
        return MemoryPipeline(self)


@pytest.fixture(autouse=True)
def isolated_services(monkeypatch):
    memory_redis = MemoryRedis()
    monkeypatch.setattr(repository, "redis_client", memory_redis)
    monkeypatch.setattr(main, "redis_client", memory_redis)
    monkeypatch.setattr(main.catalog_client, "get_product", get_catalog_product)
    return memory_redis


async def get_catalog_product(product_id: str) -> CatalogProduct:
    return CatalogProduct(id=product_id, name="Running Shoes", price=Decimal("2999.00"))


def bearer(user_id="user-1"):
    token = jwt.encode(
        {"sub": user_id, "roles": ["CUSTOMER"], "exp": int(time()) + 60},
        JWT_SECRET,
        algorithm="HS256",
    )
    return {"Authorization": f"Bearer {token}"}


def request(method, path, headers=None, json_body=None):
    async def send_request():
        transport = httpx.ASGITransport(app=main.app)
        async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
            return await client.request(method, path, headers=headers, json=json_body)

    return asyncio.run(send_request())


def test_health():
    response = request("GET", "/health")

    assert response.status_code == 200
    assert response.json() == {"status": "UP"}


def test_cart_requires_authentication():
    response = request("GET", "/api/carts/user-1")

    assert response.status_code == 401


def test_user_cannot_read_another_users_cart():
    response = request("GET", "/api/carts/user-2", headers=bearer("user-1"))

    assert response.status_code == 403


def test_get_empty_cart():
    response = request("GET", "/api/carts/user-1", headers=bearer())

    assert response.status_code == 200
    assert response.json() == {"user_id": "user-1", "items": []}


def test_add_item_uses_catalog_price_and_ignores_client_price(isolated_services):
    payload = {
        "product_id": "product-1",
        "quantity": 2,
        "name": "Forged name",
        "price": "0.01",
    }

    response = request("POST", "/api/carts/user-1/items", headers=bearer(), json_body=payload)

    assert response.status_code == 200
    assert response.json()["items"][0] == {
        "product_id": "product-1",
        "name": "Running Shoes",
        "price": "2999.00",
        "quantity": 2,
    }
    stored = Cart.model_validate_json(isolated_services.values["cart:user-1"])
    assert stored.items[0].model_dump() == {"product_id": "product-1", "quantity": 2}


def test_add_same_item_increases_quantity():
    payload = {"product_id": "product-1", "quantity": 2}

    request("POST", "/api/carts/user-1/items", headers=bearer(), json_body=payload)
    response = request("POST", "/api/carts/user-1/items", headers=bearer(), json_body=payload)

    assert response.status_code == 200
    assert response.json()["items"][0]["quantity"] == 4


def test_update_item():
    request("POST", "/api/carts/user-1/items", headers=bearer(),
            json_body={"product_id": "product-1", "quantity": 2})
    response = request("PATCH", "/api/carts/user-1/items/product-1", headers=bearer(),
                       json_body={"quantity": 5})

    assert response.status_code == 200
    assert response.json()["items"][0]["quantity"] == 5


def test_remove_item():
    request("POST", "/api/carts/user-1/items", headers=bearer(),
            json_body={"product_id": "product-1", "quantity": 2})
    response = request("DELETE", "/api/carts/user-1/items/product-1", headers=bearer())

    assert response.status_code == 200
    assert response.json()["items"] == []


def test_clear_cart():
    request("POST", "/api/carts/user-1/items", headers=bearer(),
            json_body={"product_id": "product-1", "quantity": 1})
    response = request("DELETE", "/api/carts/user-1", headers=bearer())
    cart_response = request("GET", "/api/carts/user-1", headers=bearer())

    assert response.status_code == 204
    assert cart_response.status_code == 200
    assert cart_response.json()["items"] == []


def test_concurrent_add_retries_after_watch_conflict(isolated_services):
    request("POST", "/api/carts/user-1/items", headers=bearer(),
            json_body={"product_id": "product-1", "quantity": 1})
    isolated_services.conflict_next_execute = True
    response = request("POST", "/api/carts/user-1/items", headers=bearer(),
                       json_body={"product_id": "product-1", "quantity": 3})

    assert response.status_code == 200
    assert response.json()["items"][0]["quantity"] == 5
    assert len(isolated_services.watched_keys) >= 3


def test_add_rejects_quantity_above_limit():
    response = request("POST", "/api/carts/user-1/items", headers=bearer(),
                       json_body={"product_id": "product-1", "quantity": 1001})

    assert response.status_code == 422
