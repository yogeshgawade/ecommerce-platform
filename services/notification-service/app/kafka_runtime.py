import json
import logging
import threading

from confluent_kafka import Consumer, KafkaError, TopicPartition
from pydantic import ValidationError

from .config import settings
from .processor import NotificationProcessor
from .schemas import OrderEvent

logger = logging.getLogger(__name__)


class KafkaRuntime:
    def __init__(self, processor: NotificationProcessor):
        self.processor = processor
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None

    def start(self) -> None:
        if not settings.kafka_enabled:
            logger.info("Kafka runtime disabled")
            return
        self._stop.clear()
        self._thread = threading.Thread(target=self._consume, name="notification-consumer", daemon=True)
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
            # Don't send email notifications for old orders when this worker is first deployed.
            "auto.offset.reset": "latest",
        })
        consumer.subscribe([settings.order_topic])
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
                    raw = json.loads(message.value())
                except (json.JSONDecodeError, UnicodeDecodeError, TypeError):
                    logger.exception("Ignoring malformed order event at %s[%s] offset %s",
                                     message.topic(), message.partition(), message.offset())
                    consumer.commit(message=message, asynchronous=False)
                    continue
                if not isinstance(raw, dict):
                    logger.error("Ignoring non-object order event at %s[%s] offset %s",
                                 message.topic(), message.partition(), message.offset())
                    consumer.commit(message=message, asynchronous=False)
                    continue
                event_type = raw.get("type", raw.get("eventType"))
                if event_type not in {"OrderConfirmed", "OrderCancelled"}:
                    consumer.commit(message=message, asynchronous=False)
                    continue
                try:
                    event = OrderEvent.model_validate(raw)
                except ValidationError:
                    logger.exception("Ignoring invalid notification event at %s[%s] offset %s",
                                     message.topic(), message.partition(), message.offset())
                    consumer.commit(message=message, asynchronous=False)
                    continue
                try:
                    self.processor.process_order_event(event)
                except Exception:
                    logger.exception("Notification failed; retrying Kafka message")
                    consumer.seek(TopicPartition(message.topic(), message.partition(), message.offset()))
                    self._stop.wait(5)
                else:
                    consumer.commit(message=message, asynchronous=False)
        finally:
            consumer.close()
