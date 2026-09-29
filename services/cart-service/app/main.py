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
