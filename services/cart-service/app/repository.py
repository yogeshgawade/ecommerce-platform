from .models import Cart
from .redis_client import CART_TTL_SECONDS, cart_key, redis_client


def get_cart(user_id: str) -> Cart:
    value = redis_client.get(cart_key(user_id))

    if value is None:
        return Cart(user_id=user_id)

    return Cart.model_validate_json(value)


def save_cart(cart: Cart) -> Cart:
    redis_client.set(
        cart_key(cart.user_id),
        cart.model_dump_json(),
        ex=CART_TTL_SECONDS,
    )
    return cart


def delete_cart(user_id: str) -> None:
    redis_client.delete(cart_key(user_id))


def get_cart_ttl(user_id: str) -> int:
    return redis_client.ttl(cart_key(user_id))
