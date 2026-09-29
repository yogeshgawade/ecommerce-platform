import json
import logging
import threading

from confluent_kafka import Consumer, KafkaError, TopicPartition
from pydantic import ValidationError

from .config import settings
from .schemas import ProductEvent, ReviewRatingEvent
from .search_index import SearchIndex

logger = logging.getLogger(__name__)


class KafkaRuntime:
    def __init__(self, search_index: SearchIndex):
        self.search_index = search_index
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None
        self._running = False

    @property
    def running(self) -> bool:
        return self._running and self._thread is not None and self._thread.is_alive()

    def start(self) -> None:
        if not settings.kafka_enabled:
            logger.info("Kafka consumer disabled")
            return
        self._stop.clear()
        self._thread = threading.Thread(target=self._consume, name="catalog-event-consumer", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        if self._thread and self._thread.is_alive():
            self._thread.join(timeout=10)

    def _consume(self) -> None:
        consumer = Consumer({
            "bootstrap.servers": settings.kafka_bootstrap_servers,
            "group.id": settings.kafka_group_id,
            "enable.auto.commit": False,
            "auto.offset.reset": "earliest",
        })
        consumer.subscribe([settings.kafka_topic, settings.kafka_review_topic])
        self._running = True
        try:
            while not self._stop.is_set():
                message = consumer.poll(1.0)
                if message is None:
                    continue
                if message.error():
                    if message.error().code() != KafkaError._PARTITION_EOF:
                        logger.error("Kafka consume error: %s", message.error())
                    continue
                try:
                    raw_event = json.loads(message.value())
                    if not isinstance(raw_event, dict):
                        raise ValueError("event payload must be a JSON object")
                    event_type = raw_event.get("eventType", raw_event.get("event_type"))
                    if event_type == "ReviewRatingUpdated":
                        event = ReviewRatingEvent.model_validate(raw_event)
                    else:
                        event = ProductEvent.model_validate(raw_event)
                except (json.JSONDecodeError, UnicodeDecodeError, TypeError, ValueError, ValidationError):
                    logger.exception(
                        "Ignoring invalid catalog event at %s[%s] offset %s",
                        message.topic(), message.partition(), message.offset(),
                    )
                    consumer.commit(message=message, asynchronous=False)
                    continue

                if isinstance(event, ReviewRatingEvent):
                    handler = self.search_index.apply_rating_event
                elif event.event_type in {"ProductCreated", "ProductUpdated", "ProductDeleted"}:
                    handler = self.search_index.apply_event
                else:
                    consumer.commit(message=message, asynchronous=False)
                    continue

                try:
                    handler(event)
                except Exception:
                    logger.exception("Could not apply catalog event; retrying Kafka message")
                    consumer.seek(TopicPartition(message.topic(), message.partition(), message.offset()))
                    self._stop.wait(settings.kafka_retry_seconds)
                else:
                    consumer.commit(message=message, asynchronous=False)
        finally:
            self._running = False
            consumer.close()
