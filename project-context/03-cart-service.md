# 03-cart-service


---

## File: `services/cart-service/app/auth.py`

```python
import os

import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from jwt import InvalidTokenError

from dotenv import load_dotenv

load_dotenv()

JWT_SECRET = os.getenv(
    "APP_JWT_SECRET",
    "local-development-secret-change-this-to-a-long-random-value",
)
if len(JWT_SECRET.encode("utf-8")) < 32:
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
        claims = jwt.decode(credentials.credentials, JWT_SECRET, algorithms=["HS256"])
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


async def require_cart_owner(
    user_id: str,
    token_user_id: str = Depends(authenticated_user_id),
) -> str:
    if user_id != token_user_id:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Cannot access another user's cart")
    return token_user_id

```

---

## File: `services/cart-service/app/catalog_client.py`

```python
import os
from decimal import Decimal, InvalidOperation
from urllib.parse import quote

import httpx
from fastapi import HTTPException, status
from pydantic import BaseModel, Field, ValidationError


class CatalogProduct(BaseModel):
    id: str
    name: str = Field(min_length=1)
    price: Decimal = Field(gt=0)


class CatalogClient:
    def __init__(self, base_url: str | None = None):
        self.base_url = (base_url or os.getenv("CATALOG_SERVICE_URL", "http://localhost:8081")).rstrip("/")
        self.client = httpx.AsyncClient(timeout=httpx.Timeout(3.0, connect=1.0))

    async def get_product(self, product_id: str) -> CatalogProduct:
        try:
            response = await self.client.get(f"{self.base_url}/api/products/{quote(product_id, safe='')}")
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
        except (ValueError, ValidationError, InvalidOperation) as exc:
            raise HTTPException(
                status_code=status.HTTP_502_BAD_GATEWAY,
                detail="Catalog service returned invalid product data",
            ) from exc
        if product.id != product_id:
            raise HTTPException(
                status_code=status.HTTP_502_BAD_GATEWAY,
                detail="Catalog service returned a mismatched product",
            )
        return product

    async def close(self) -> None:
        await self.client.aclose()


catalog_client = CatalogClient()

```

---

## File: `services/cart-service/app/__init__.py`

```python

```

---

## File: `services/cart-service/app/main.py`

```python
from contextlib import asynccontextmanager
from collections.abc import Callable

from fastapi import Depends, FastAPI, HTTPException, status

from .auth import require_cart_owner
from .catalog_client import CatalogProduct, catalog_client
from .models import (
    AddCartItemRequest,
    Cart,
    CartItem,
    CartItemResponse,
    CartResponse,
    UpdateCartItemRequest,
)
from .redis_client import redis_client
from .repository import CartWriteConflict, delete_cart, get_cart, get_cart_ttl, mutate_cart


@asynccontextmanager
async def lifespan(app: FastAPI):
    await redis_client.ping()
    yield
    await catalog_client.close()


app = FastAPI(
    title="Cart Service",
    version="0.2.0",
    lifespan=lifespan,
)


@app.get("/health")
async def health() -> dict[str, str]:
    await redis_client.ping()
    return {"status": "UP"}


@app.get("/api/carts/{user_id}", response_model=CartResponse)
async def get_user_cart(
    user_id: str,
    _: str = Depends(require_cart_owner),
) -> CartResponse:
    return await render_cart(await get_cart(user_id))


@app.get("/api/carts/{user_id}/ttl")
async def get_user_cart_ttl(
    user_id: str,
    _: str = Depends(require_cart_owner),
) -> dict[str, int]:
    return {"ttl_seconds": await get_cart_ttl(user_id)}


@app.post("/api/carts/{user_id}/items", response_model=CartResponse)
async def add_item(
    user_id: str,
    request: AddCartItemRequest,
    _: str = Depends(require_cart_owner),
) -> CartResponse:
    product = await catalog_client.get_product(request.product_id)

    def add_to_cart(cart: Cart) -> None:
        for index, existing_item in enumerate(cart.items):
            if existing_item.product_id == request.product_id:
                new_quantity = existing_item.quantity + request.quantity
                if new_quantity > 1000:
                    raise HTTPException(
                        status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                        detail="A cart item cannot exceed 1000 units",
                    )
                cart.items[index] = CartItem(product_id=request.product_id, quantity=new_quantity)
                return
        cart.items.append(CartItem(product_id=request.product_id, quantity=request.quantity))

    cart = await update_cart(user_id, add_to_cart)
    return await render_cart(cart, {product.id: product})


@app.patch(
    "/api/carts/{user_id}/items/{product_id}",
    response_model=CartResponse,
)
async def update_item(
    user_id: str,
    product_id: str,
    request: UpdateCartItemRequest,
    _: str = Depends(require_cart_owner),
) -> CartResponse:
    def update_quantity(cart: Cart) -> None:
        for index, item in enumerate(cart.items):
            if item.product_id == product_id:
                cart.items[index] = CartItem(product_id=product_id, quantity=request.quantity)
                return
        raise HTTPException(status_code=404, detail="Cart item not found")

    return await render_cart(await update_cart(user_id, update_quantity))


@app.delete(
    "/api/carts/{user_id}/items/{product_id}",
    response_model=CartResponse,
)
async def remove_item(
    user_id: str,
    product_id: str,
    _: str = Depends(require_cart_owner),
) -> CartResponse:
    def remove_from_cart(cart: Cart) -> None:
        original_count = len(cart.items)
        cart.items = [item for item in cart.items if item.product_id != product_id]
        if len(cart.items) == original_count:
            raise HTTPException(status_code=404, detail="Cart item not found")

    return await render_cart(await update_cart(user_id, remove_from_cart))


@app.delete("/api/carts/{user_id}", status_code=204)
async def clear_cart(
    user_id: str,
    _: str = Depends(require_cart_owner),
) -> None:
    await delete_cart(user_id)


async def render_cart(cart: Cart, known_products: dict[str, CatalogProduct] | None = None) -> CartResponse:
    product_cache = known_products or {}
    response_items: list[CartItemResponse] = []
    for item in cart.items:
        product = product_cache.get(item.product_id)
        if product is None:
            product = await catalog_client.get_product(item.product_id)
            product_cache[item.product_id] = product
        response_items.append(
            CartItemResponse(
                product_id=item.product_id,
                name=product.name,
                price=product.price,
                quantity=item.quantity,
            )
        )
    return CartResponse(user_id=cart.user_id, items=response_items)


async def update_cart(user_id: str, update: Callable[[Cart], None]) -> Cart:
    try:
        return await mutate_cart(user_id, update)
    except CartWriteConflict as exc:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Cart changed concurrently; retry the request",
        ) from exc

```

---

## File: `services/cart-service/app/models.py`

```python
from decimal import Decimal

from pydantic import BaseModel, Field


class AddCartItemRequest(BaseModel):
    product_id: str = Field(min_length=1, max_length=200)
    quantity: int = Field(gt=0, le=1000)


class CartItem(BaseModel):
    product_id: str
    quantity: int = Field(gt=0, le=1000)


class CartItemResponse(BaseModel):
    product_id: str
    name: str
    price: Decimal
    quantity: int


class UpdateCartItemRequest(BaseModel):
    quantity: int = Field(gt=0, le=1000)


class Cart(BaseModel):
    user_id: str
    items: list[CartItem] = Field(default_factory=list)


class CartResponse(BaseModel):
    user_id: str
    items: list[CartItemResponse] = Field(default_factory=list)

```

---

## File: `services/cart-service/app/redis_client.py`

```python
import os

from redis.asyncio import Redis
from dotenv import load_dotenv

load_dotenv()

REDIS_HOST = os.getenv("REDIS_HOST", "localhost")
REDIS_PORT = int(os.getenv("REDIS_PORT", "6379"))
REDIS_PASSWORD = os.getenv("REDIS_PASSWORD", "redis_dev_password")
CART_TTL_SECONDS = int(os.getenv("CART_TTL_SECONDS", "86400"))

redis_client = Redis(
    host=REDIS_HOST,
    port=REDIS_PORT,
    password=REDIS_PASSWORD,
    decode_responses=True,
)


def cart_key(user_id: str) -> str:
    return f"cart:{user_id}"

```

---

## File: `services/cart-service/app/repository.py`

```python
from collections.abc import Callable

from redis.exceptions import WatchError

from .models import Cart
from .redis_client import CART_TTL_SECONDS, cart_key, redis_client

MAX_WRITE_RETRIES = 5


class CartWriteConflict(Exception):
    pass


async def get_cart(user_id: str) -> Cart:
    value = await redis_client.get(cart_key(user_id))

    if value is None:
        return Cart(user_id=user_id)

    return Cart.model_validate_json(value)


async def mutate_cart(user_id: str, update: Callable[[Cart], None]) -> Cart:
    key = cart_key(user_id)
    for _ in range(MAX_WRITE_RETRIES):
        async with redis_client.pipeline() as pipeline:
            try:
                await pipeline.watch(key)
                value = await pipeline.get(key)
                cart = Cart(user_id=user_id) if value is None else Cart.model_validate_json(value)
                update(cart)
                pipeline.multi()
                pipeline.set(key, cart.model_dump_json(), ex=CART_TTL_SECONDS)
                await pipeline.execute()
                return cart
            except WatchError:
                continue

    raise CartWriteConflict(f"Could not update cart for user {user_id} after concurrent changes")


async def delete_cart(user_id: str) -> None:
    await redis_client.delete(cart_key(user_id))


async def get_cart_ttl(user_id: str) -> int:
    return await redis_client.ttl(cart_key(user_id))

```

---

## File: `services/cart-service/.pytest_cache/README.md`

```markdown
# pytest cache directory #

This directory contains data from the pytest's cache plugin,
which provides the `--lf` and `--ff` options, as well as the `cache` fixture.

**Do not** commit this to version control.

See [the docs](https://docs.pytest.org/en/stable/how-to/cache.html) for more information.

```

---

## File: `services/cart-service/README.md`

```markdown
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

```

---

## File: `services/cart-service/tests/__init__.py`

```python

```

---

## File: `services/cart-service/tests/test_cart_service.py`

```python
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

```
