import os

import redis
from dotenv import load_dotenv

load_dotenv()

REDIS_HOST = os.getenv("REDIS_HOST", "localhost")
REDIS_PORT = int(os.getenv("REDIS_PORT", "6379"))
REDIS_PASSWORD = os.getenv("REDIS_PASSWORD", "redis_dev_password")
CART_TTL_SECONDS = int(os.getenv("CART_TTL_SECONDS", "86400"))

redis_client = redis.Redis(
    host=REDIS_HOST,
    port=REDIS_PORT,
    password=REDIS_PASSWORD,
    decode_responses=True,
)


def cart_key(user_id: str) -> str:
    return f"cart:{user_id}"
