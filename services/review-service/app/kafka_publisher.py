import json
import logging
from datetime import datetime, timezone
from uuid import uuid4

from confluent_kafka import Producer

from .config import settings

logger = logging.getLogger(__name__)


class RatingPublisher:
    def __init__(self):
        self.producer = Producer({"bootstrap.servers": settings.kafka_bootstrap_servers}) if settings.kafka_enabled else None

    def publish(self, product_id: str, average_rating: float | None, review_count: int) -> None:
        if self.producer is None:
            return
        event = {
            "eventId": str(uuid4()),
            "eventType": "ReviewRatingUpdated",
            "occurredAt": datetime.now(timezone.utc).isoformat(),
            "productId": product_id,
            "averageRating": average_rating,
            "reviewCount": review_count,
        }
        delivery_errors: list[str] = []

        def on_delivery(error, _message):
            if error is not None:
                delivery_errors.append(str(error))

        self.producer.produce(
            settings.kafka_topic,
            key=product_id,
            value=json.dumps(event, separators=(",", ":")),
            on_delivery=on_delivery,
        )
        remaining = self.producer.flush(5.0)
        if remaining:
            raise TimeoutError("Timed out publishing product rating update")
        if delivery_errors:
            raise RuntimeError(f"Could not publish product rating update: {delivery_errors[0]}")

    def close(self) -> None:
        if self.producer is not None:
            self.producer.flush(5.0)
