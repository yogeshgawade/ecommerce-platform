import json
import logging
import threading
from datetime import datetime, timezone
from decimal import Decimal

from confluent_kafka import Consumer, KafkaError, Producer, TopicPartition
from pydantic import ValidationError
from sqlalchemy import select

from .config import settings
from .database import SessionLocal
from .models import PaymentOutbox
from .processor import PaymentProcessor
from .schemas import PaymentRequested

logger = logging.getLogger(__name__)


class KafkaRuntime:
    def __init__(self, processor: PaymentProcessor):
        self.processor = processor
        self._stop = threading.Event()
        self._consumer_thread: threading.Thread | None = None
        self._outbox_thread: threading.Thread | None = None
        self._producer: Producer | None = None

    def start(self) -> None:
        if not settings.kafka_enabled:
            logger.info("Kafka runtime disabled")
            return
        self._stop.clear()
        self._producer = Producer({"bootstrap.servers": settings.kafka_bootstrap_servers})
        self._consumer_thread = threading.Thread(target=self._consume, name="payment-consumer", daemon=True)
        self._outbox_thread = threading.Thread(target=self._publish_outbox, name="payment-outbox", daemon=True)
        self._consumer_thread.start()
        self._outbox_thread.start()

    def stop(self) -> None:
        self._stop.set()
        for thread in (self._consumer_thread, self._outbox_thread):
            if thread and thread.is_alive():
                thread.join(timeout=10)
        if self._producer:
            self._producer.flush(10)

    def _consume(self) -> None:
        consumer = Consumer({
            "bootstrap.servers": settings.kafka_bootstrap_servers,
            "group.id": settings.kafka_group_id,
            "enable.auto.commit": False,
            "auto.offset.reset": "earliest",
        })
        consumer.subscribe([settings.payment_topic])
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
                    raw = json.loads(message.value(), parse_float=Decimal)
                    if isinstance(raw, dict) and raw.get("type") == "PaymentRequested":
                        request = PaymentRequested.model_validate(raw)
                        self.processor.process_payment_request(request)
                    consumer.commit(message=message, asynchronous=False)
                except (json.JSONDecodeError, UnicodeDecodeError, TypeError):
                    logger.exception("Ignoring malformed Kafka message at %s[%s] offset %s",
                                     message.topic(), message.partition(), message.offset())
                    consumer.commit(message=message, asynchronous=False)
                except ValidationError:
                    logger.exception("Ignoring invalid PaymentRequested at %s[%s] offset %s",
                                     message.topic(), message.partition(), message.offset())
                    consumer.commit(message=message, asynchronous=False)
                except Exception:
                    logger.exception("Payment event processing failed; retrying Kafka message")
                    consumer.seek(TopicPartition(message.topic(), message.partition(), message.offset()))
                    self._stop.wait(1)
        finally:
            consumer.close()

    def _publish_outbox(self) -> None:
        while not self._stop.is_set():
            did_work = False
            try:
                with SessionLocal.begin() as session:
                    now = datetime.now(timezone.utc)
                    rows = list(session.scalars(
                        select(PaymentOutbox)
                        .where(PaymentOutbox.published_at.is_(None))
                        .where((PaymentOutbox.next_attempt_at.is_(None)) | (PaymentOutbox.next_attempt_at <= now))
                        .order_by(PaymentOutbox.created_at, PaymentOutbox.id)
                        .limit(50)
                        .with_for_update(skip_locked=True)
                    ))
                    for row in rows:
                        did_work = True
                        try:
                            self._send(row.topic, row.message_key, row.payload)
                            row.mark_published()
                        except Exception as exc:
                            logger.exception("Failed to publish payment outbox row %s", row.id)
                            row.mark_failed(str(exc))
            except Exception:
                logger.exception("Payment outbox poll failed")
            if not did_work:
                self._stop.wait(settings.outbox_poll_interval)

    def _send(self, topic: str, key: str, payload: str) -> None:
        if self._producer is None:
            raise RuntimeError("Kafka producer is not running")
        outcome: list[Exception | None] = []
        self._producer.produce(topic, key=key.encode(), value=payload.encode(), callback=lambda err, _msg: outcome.append(
            RuntimeError(str(err)) if err else None
        ))
        remaining = self._producer.flush(10)
        if remaining or not outcome:
            raise TimeoutError("Kafka did not acknowledge payment event")
        if outcome[0]:
            raise outcome[0]
