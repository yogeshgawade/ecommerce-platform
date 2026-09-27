from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException

from .models import Cart, CartItem, UpdateCartItemRequest
from .redis_client import redis_client
from .repository import delete_cart, get_cart, get_cart_ttl, save_cart


@asynccontextmanager
async def lifespan(app: FastAPI):
    redis_client.ping()
    yield


app = FastAPI(
    title="Cart Service",
    version="0.1.0",
    lifespan=lifespan,
)


@app.get("/health")
def health() -> dict[str, str]:
    redis_client.ping()
    return {"status": "UP"}


@app.get("/api/carts/{user_id}", response_model=Cart)
def get_user_cart(user_id: str) -> Cart:
    return get_cart(user_id)


@app.get("/api/carts/{user_id}/ttl")
def get_user_cart_ttl(user_id: str) -> dict[str, int]:
    return {"ttl_seconds": get_cart_ttl(user_id)}


@app.post("/api/carts/{user_id}/items", response_model=Cart)
def add_item(user_id: str, item: CartItem) -> Cart:
    cart = get_cart(user_id)

    for existing_item in cart.items:
        if existing_item.product_id == item.product_id:
            existing_item.quantity += item.quantity
            break
    else:
        cart.items.append(item)

    return save_cart(cart)


@app.patch(
    "/api/carts/{user_id}/items/{product_id}",
    response_model=Cart,
)
def update_item(
    user_id: str,
    product_id: str,
    request: UpdateCartItemRequest,
) -> Cart:
    cart = get_cart(user_id)

    for item in cart.items:
        if item.product_id == product_id:
            item.quantity = request.quantity
            return save_cart(cart)

    raise HTTPException(status_code=404, detail="Cart item not found")


@app.delete(
    "/api/carts/{user_id}/items/{product_id}",
    response_model=Cart,
)
def remove_item(user_id: str, product_id: str) -> Cart:
    cart = get_cart(user_id)
    original_count = len(cart.items)

    cart.items = [
        item for item in cart.items
        if item.product_id != product_id
    ]

    if len(cart.items) == original_count:
        raise HTTPException(status_code=404, detail="Cart item not found")

    return save_cart(cart)


@app.delete("/api/carts/{user_id}", status_code=204)
def clear_cart(user_id: str) -> None:
    delete_cart(user_id)
