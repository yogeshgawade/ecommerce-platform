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
