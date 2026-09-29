import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    elasticsearch_url: str = os.getenv("ELASTICSEARCH_URL", "http://localhost:9200")
    elasticsearch_index: str = os.getenv("ELASTICSEARCH_INDEX", "products")
    kafka_bootstrap_servers: str = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    kafka_topic: str = os.getenv("KAFKA_CATALOG_TOPIC", "catalog-events")
    kafka_review_topic: str = os.getenv("KAFKA_REVIEW_TOPIC", "review-events")
    kafka_group_id: str = os.getenv("KAFKA_CONSUMER_GROUP_ID", "search-service-group")
    kafka_enabled: bool = os.getenv("KAFKA_ENABLED", "true").strip().lower() in {
        "1", "true", "yes", "on"
    }
    kafka_retry_seconds: float = float(os.getenv("KAFKA_RETRY_SECONDS", "5"))
    max_page_size: int = int(os.getenv("SEARCH_MAX_PAGE_SIZE", "100"))


settings = Settings()
