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
        "NOTIFICATION_DATABASE_URL",
        "postgresql+psycopg://ecommerce:ecommerce_dev_password@localhost:5432/notification_db",
    )
    kafka_bootstrap_servers: str = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    kafka_group_id: str = os.getenv("KAFKA_CONSUMER_GROUP_ID", "notification-service-group")
    order_topic: str = os.getenv("KAFKA_ORDER_TOPIC", "order-events")
    kafka_enabled: bool = _bool_env("KAFKA_ENABLED", True)
    email_backend: str = os.getenv("NOTIFICATION_EMAIL_BACKEND", "log").strip().lower()
    smtp_host: str = os.getenv("SMTP_HOST", "")
    smtp_port: int = int(os.getenv("SMTP_PORT", "587"))
    smtp_username: str = os.getenv("SMTP_USERNAME", "")
    smtp_password: str = os.getenv("SMTP_PASSWORD", "")
    smtp_from: str = os.getenv("SMTP_FROM", "")
    smtp_starttls: bool = _bool_env("SMTP_STARTTLS", True)
    smtp_ssl: bool = _bool_env("SMTP_SSL", False)


settings = Settings()
