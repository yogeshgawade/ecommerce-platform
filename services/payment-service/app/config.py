import os
from dataclasses import dataclass


def _bool_env(name: str, default: bool) -> bool:
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on"}


@dataclass(frozen=True)
class Settings:
    database_url: str = os.getenv(
        "PAYMENT_DATABASE_URL",
        "postgresql+psycopg://ecommerce:ecommerce_dev_password@localhost:5432/payment_db",
    )
    jwt_secret: str = os.getenv(
        "APP_JWT_SECRET", "local-development-secret-change-this-to-a-long-random-value"
    )
    stripe_secret_key: str = os.getenv("STRIPE_SECRET_KEY", "")
    stripe_webhook_secret: str = os.getenv("STRIPE_WEBHOOK_SECRET", "")
    kafka_bootstrap_servers: str = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    kafka_group_id: str = os.getenv("KAFKA_CONSUMER_GROUP_ID", "payment-service-group")
    payment_topic: str = os.getenv("KAFKA_PAYMENT_TOPIC", "payment-events")
    outbox_poll_interval: float = float(os.getenv("PAYMENT_OUTBOX_POLL_INTERVAL", "1"))
    kafka_enabled: bool = _bool_env("KAFKA_ENABLED", True)


settings = Settings()
