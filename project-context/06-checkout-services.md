# 06-checkout-services


---

## File: `services/notification-service/alembic.ini`

```properties
[alembic]
script_location = migrations
prepend_sys_path = .
sqlalchemy.url = postgresql+psycopg://ecommerce:ecommerce_dev_password@localhost:5432/notification_db

[loggers]
keys = root,sqlalchemy,alembic

[handlers]
keys = console

[formatters]
keys = generic

[logger_root]
level = WARN
handlers = console
qualname =

[logger_sqlalchemy]
level = WARN
handlers =
qualname = sqlalchemy.engine

[logger_alembic]
level = INFO
handlers = console
qualname = alembic

[handler_console]
class = StreamHandler
args = (sys.stderr,)
level = NOTSET
formatter = generic

[formatter_generic]
format = %(levelname)-5.5s [%(name)s] %(message)s
datefmt = %H:%M:%S

```

---

## File: `services/notification-service/app/config.py`

```python
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

```

---

## File: `services/notification-service/app/database.py`

```python
from sqlalchemy import create_engine
from sqlalchemy.orm import DeclarativeBase, sessionmaker

from .config import settings


class Base(DeclarativeBase):
    pass


engine = create_engine(settings.database_url, pool_pre_ping=True)
SessionLocal = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)

```

---

## File: `services/notification-service/app/email_sender.py`

```python
import logging
import smtplib
from email.message import EmailMessage
from typing import Protocol

from .config import settings

logger = logging.getLogger(__name__)


class EmailSender(Protocol):
    def send(self, recipient: str, subject: str, body: str) -> bool: ...


class LogEmailSender:
    """Development sender: record simulated delivery without contacting an email provider."""

    def send(self, recipient: str, subject: str, body: str) -> bool:
        logger.info("Email simulated (no SMTP configured): recipient=%s subject=%s", recipient, subject)
        return False


class SmtpEmailSender:
    def __init__(self):
        if not settings.smtp_host or not settings.smtp_from:
            raise ValueError("SMTP_HOST and SMTP_FROM are required when NOTIFICATION_EMAIL_BACKEND=smtp")

    def send(self, recipient: str, subject: str, body: str) -> bool:
        message = EmailMessage()
        message["From"] = settings.smtp_from
        message["To"] = recipient
        message["Subject"] = subject
        message.set_content(body)

        if settings.smtp_ssl:
            client = smtplib.SMTP_SSL(settings.smtp_host, settings.smtp_port, timeout=15)
        else:
            client = smtplib.SMTP(settings.smtp_host, settings.smtp_port, timeout=15)
        with client:
            if settings.smtp_starttls and not settings.smtp_ssl:
                client.starttls()
            if settings.smtp_username:
                client.login(settings.smtp_username, settings.smtp_password)
            client.send_message(message)
        return True


def build_email_sender() -> EmailSender:
    if settings.email_backend == "smtp":
        return SmtpEmailSender()
    if settings.email_backend == "log":
        return LogEmailSender()
    raise ValueError("NOTIFICATION_EMAIL_BACKEND must be 'log' or 'smtp'")

```

---

## File: `services/notification-service/app/__init__.py`

```python


```

---

## File: `services/notification-service/app/kafka_runtime.py`

```python
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

```

---

## File: `services/notification-service/app/main.py`

```python
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from sqlalchemy import text

from .database import SessionLocal
from .email_sender import build_email_sender
from .kafka_runtime import KafkaRuntime
from .processor import NotificationProcessor

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

processor = NotificationProcessor(SessionLocal, build_email_sender())
kafka_runtime = KafkaRuntime(processor)


@asynccontextmanager
async def lifespan(_app: FastAPI):
    kafka_runtime.start()
    yield
    kafka_runtime.stop()


app = FastAPI(title="Notification Service", version="1.0.0", lifespan=lifespan)


@app.get("/health")
def health() -> dict[str, str]:
    try:
        with SessionLocal() as session:
            session.execute(text("select 1"))
    except Exception as exc:
        logger.exception("Notification database health check failed")
        raise HTTPException(status_code=503, detail="Notification database is unavailable") from exc
    return {"status": "UP"}

```

---

## File: `services/notification-service/app/models.py`

```python
from datetime import datetime, timezone

from sqlalchemy import DateTime, Index, Integer, String
from sqlalchemy.orm import Mapped, mapped_column

from .database import Base


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


class NotificationDelivery(Base):
    __tablename__ = "notification_deliveries"
    __table_args__ = (Index("notification_order_created_idx", "order_id", "created_at"),)

    event_id: Mapped[str] = mapped_column(String(255), primary_key=True)
    order_id: Mapped[str] = mapped_column(String(36), nullable=False)
    event_type: Mapped[str] = mapped_column(String(64), nullable=False)
    recipient_email: Mapped[str | None] = mapped_column(String(254))
    subject: Mapped[str] = mapped_column(String(255), nullable=False)
    status: Mapped[str] = mapped_column(String(32), nullable=False)
    attempts: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    last_error: Mapped[str | None] = mapped_column(String(1000))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)
    sent_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

```

---

## File: `services/notification-service/app/processor.py`

```python
import logging
from sqlalchemy.orm import Session, sessionmaker

from .email_sender import EmailSender
from .models import NotificationDelivery, utc_now
from .schemas import OrderEvent

logger = logging.getLogger(__name__)


class NotificationProcessor:
    def __init__(self, session_factory: sessionmaker[Session], email_sender: EmailSender):
        self.session_factory = session_factory
        self.email_sender = email_sender

    def process_order_event(self, event: OrderEvent) -> str:
        subject, body = self._message(event)
        with self.session_factory.begin() as session:
            delivery = session.get(NotificationDelivery, event.event_id)
            if delivery is not None and delivery.status in {"SENT", "SIMULATED", "SKIPPED"}:
                return delivery.status
            if delivery is None:
                delivery = NotificationDelivery(
                    event_id=event.event_id,
                    order_id=event.order_id,
                    event_type=event.type,
                    recipient_email=str(event.customer_email) if event.customer_email else None,
                    subject=subject,
                    status="PENDING",
                    attempts=0,
                    created_at=utc_now(),
                )
                session.add(delivery)
                session.flush()
            delivery.attempts += 1
            delivery.last_error = None
            recipient = delivery.recipient_email
            attempts = delivery.attempts

        if not recipient:
            with self.session_factory.begin() as session:
                delivery = session.get(NotificationDelivery, event.event_id)
                delivery.status = "SKIPPED"
                delivery.last_error = "Order event contains no customer email address"
            logger.warning("Skipping notification for order %s: event has no customer email", event.order_id)
            return "SKIPPED"

        try:
            sent = self.email_sender.send(recipient, subject, body)
        except Exception as exc:
            with self.session_factory.begin() as session:
                delivery = session.get(NotificationDelivery, event.event_id)
                delivery.status = "PENDING"
                delivery.attempts = attempts
                delivery.last_error = str(exc)[:1000]
            raise

        with self.session_factory.begin() as session:
            delivery = session.get(NotificationDelivery, event.event_id)
            delivery.status = "SENT" if sent else "SIMULATED"
            delivery.last_error = None
            delivery.sent_at = utc_now() if sent else None
        return "SENT" if sent else "SIMULATED"

    def _message(self, event: OrderEvent) -> tuple[str, str]:
        if event.type == "OrderConfirmed":
            subject = f"Order {event.order_id} confirmed"
            body = f"Your order {event.order_id} is confirmed."
        else:
            subject = f"Order {event.order_id} cancelled"
            body = f"Your order {event.order_id} was cancelled."
            if event.reason:
                body += f" Reason: {event.reason}"
        if event.total_amount is not None and event.currency:
            body += f" Order total: {event.total_amount:.2f} {event.currency.upper()}."
        return subject, body

```

---

## File: `services/notification-service/app/schemas.py`

```python
from decimal import Decimal
from typing import Literal

from pydantic import BaseModel, ConfigDict, EmailStr, Field


class OrderEvent(BaseModel):
    model_config = ConfigDict(extra="ignore")

    type: Literal["OrderConfirmed", "OrderCancelled"]
    event_id: str = Field(alias="eventId", min_length=1, max_length=255)
    order_id: str = Field(alias="orderId", min_length=1, max_length=36)
    user_id: str | None = Field(default=None, alias="userId", max_length=255)
    customer_email: EmailStr | None = Field(default=None, alias="customerEmail")
    total_amount: Decimal | None = Field(default=None, alias="totalAmount", gt=0)
    currency: str | None = Field(default=None, min_length=3, max_length=3)
    reason: str | None = Field(default=None, max_length=1000)

```

---

## File: `services/notification-service/migrations/env.py`

```python
from alembic import context
from sqlalchemy import engine_from_config, pool

from app.config import settings
from app.models import Base

config = context.config
config.set_main_option("sqlalchemy.url", settings.database_url.replace("%", "%%"))
target_metadata = Base.metadata


def run_migrations_offline() -> None:
    context.configure(
        url=settings.database_url,
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
        compare_type=True,
    )
    with context.begin_transaction():
        context.run_migrations()


def run_migrations_online() -> None:
    connectable = engine_from_config(
        config.get_section(config.config_ini_section, {}),
        prefix="sqlalchemy.",
        poolclass=pool.NullPool,
    )
    with connectable.connect() as connection:
        context.configure(connection=connection, target_metadata=target_metadata, compare_type=True)
        with context.begin_transaction():
            context.run_migrations()


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()

```

---

## File: `services/notification-service/migrations/versions/0001_create_notification_deliveries.py`

```python
"""Create notification delivery log."""

from alembic import op
import sqlalchemy as sa

revision = "0001_notification_deliveries"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "notification_deliveries",
        sa.Column("event_id", sa.String(length=255), primary_key=True),
        sa.Column("order_id", sa.String(length=36), nullable=False),
        sa.Column("event_type", sa.String(length=64), nullable=False),
        sa.Column("recipient_email", sa.String(length=254), nullable=True),
        sa.Column("subject", sa.String(length=255), nullable=False),
        sa.Column("status", sa.String(length=32), nullable=False),
        sa.Column("attempts", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("last_error", sa.String(length=1000), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("sent_at", sa.DateTime(timezone=True), nullable=True),
    )
    op.create_index("notification_order_created_idx", "notification_deliveries", ["order_id", "created_at"])


def downgrade() -> None:
    op.drop_index("notification_order_created_idx", table_name="notification_deliveries")
    op.drop_table("notification_deliveries")

```

---

## File: `services/notification-service/.pytest_cache/README.md`

```markdown
# pytest cache directory #

This directory contains data from the pytest's cache plugin,
which provides the `--lf` and `--ff` options, as well as the `cache` fixture.

**Do not** commit this to version control.

See [the docs](https://docs.pytest.org/en/stable/how-to/cache.html) for more information.

```

---

## File: `services/notification-service/README.md`

```markdown
# Notification Service

The notification service consumes `OrderConfirmed` and `OrderCancelled` events from `order-events`. It deduplicates notifications by event ID and records delivery attempts in its own `notification_db` database.

For local development, the default `NOTIFICATION_EMAIL_BACKEND=log` simulates delivery and records the status as `SIMULATED`; it does not contact an email provider. Set `NOTIFICATION_EMAIL_BACKEND=smtp` and configure `SMTP_HOST`, `SMTP_PORT`, `SMTP_FROM`, and optional SMTP credentials to send email. SMTP failures leave the Kafka message uncommitted so it is retried.

Order events include the customer's email from the signed auth token. The notification worker is internal and has no public API beyond its database-backed `/health` check.

## Local checks

```sh
python -m pip install -r requirements-dev.txt
pytest -q
```

```

---

## File: `services/notification-service/tests/test_processor.py`

```python
import pytest
from sqlalchemy import create_engine, select
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from app.database import Base
from app.models import NotificationDelivery
from app.processor import NotificationProcessor
from app.schemas import OrderEvent


@pytest.fixture
def session_factory():
    engine = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    Base.metadata.create_all(engine)
    yield sessionmaker(bind=engine, expire_on_commit=False)
    Base.metadata.drop_all(engine)
    engine.dispose()


class FakeEmailSender:
    def __init__(self, raises=None, sent=True):
        self.raises = raises
        self.sent = sent
        self.messages = []

    def send(self, recipient, subject, body):
        self.messages.append((recipient, subject, body))
        if self.raises:
            raise self.raises
        return self.sent


def event(event_type="OrderConfirmed", event_id="evt-1", email="customer@example.com"):
    raw = {
        "type": event_type,
        "eventId": event_id,
        "orderId": "order-1",
        "userId": "user-1",
        "customerEmail": email,
        "totalAmount": "29.95",
        "currency": "USD",
        "reason": "Payment failed",
    }
    return OrderEvent.model_validate(raw)


def test_confirmed_order_sends_once_and_deduplicates(session_factory):
    sender = FakeEmailSender()
    processor = NotificationProcessor(session_factory, sender)
    order_event = event()

    assert processor.process_order_event(order_event) == "SENT"
    assert processor.process_order_event(order_event) == "SENT"

    assert len(sender.messages) == 1
    recipient, subject, body = sender.messages[0]
    assert recipient == "customer@example.com"
    assert subject == "Order order-1 confirmed"
    assert "29.95 USD" in body
    with session_factory() as session:
        delivery = session.get(NotificationDelivery, "evt-1")
        assert delivery.status == "SENT"
        assert delivery.attempts == 1


def test_cancelled_order_includes_reason(session_factory):
    sender = FakeEmailSender()
    processor = NotificationProcessor(session_factory, sender)

    processor.process_order_event(event("OrderCancelled"))

    assert "cancelled" in sender.messages[0][1]
    assert "Payment failed" in sender.messages[0][2]


def test_missing_recipient_is_skipped_and_logged(session_factory):
    sender = FakeEmailSender()
    processor = NotificationProcessor(session_factory, sender)

    assert processor.process_order_event(event(email=None)) == "SKIPPED"
    assert sender.messages == []
    with session_factory() as session:
        delivery = session.get(NotificationDelivery, "evt-1")
        assert delivery.status == "SKIPPED"


def test_smtp_failure_leaves_notification_pending_for_retry(session_factory):
    sender = FakeEmailSender(raises=RuntimeError("SMTP unavailable"))
    processor = NotificationProcessor(session_factory, sender)

    with pytest.raises(RuntimeError, match="SMTP unavailable"):
        processor.process_order_event(event())

    with session_factory() as session:
        delivery = session.scalar(select(NotificationDelivery))
        assert delivery.status == "PENDING"
        assert delivery.attempts == 1
        assert delivery.last_error == "SMTP unavailable"


def test_log_sender_marks_delivery_as_simulated(session_factory):
    sender = FakeEmailSender(sent=False)
    processor = NotificationProcessor(session_factory, sender)

    assert processor.process_order_event(event()) == "SIMULATED"
    with session_factory() as session:
        delivery = session.get(NotificationDelivery, "evt-1")
        assert delivery.status == "SIMULATED"

```

---

## File: `services/order-service/build.gradle`

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.1.1'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.ecommerce'
version = '0.0.1-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-flyway'
    implementation 'org.springframework.boot:spring-boot-starter-kafka'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    implementation 'com.fasterxml.jackson.datatype:jackson-datatype-jsr310'
    implementation 'org.flywaydb:flyway-database-postgresql'
    implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'
    runtimeOnly 'org.postgresql:postgresql'
    testImplementation 'org.springframework.boot:spring-boot-starter-actuator-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-data-jpa-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-kafka-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-security-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-validation-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
    testRuntimeOnly 'com.h2database:h2'
}

tasks.named('test') {
    useJUnitPlatform()
}

```

---

## File: `services/order-service/gradle/wrapper/gradle-wrapper.properties`

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-9.7.1-bin.zip
networkTimeout=120000
retries=0
retryBackOffMs=500
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists

```

---

## File: `services/order-service/README.md`

```markdown
# Order Service

The order service owns order records and immutable product/price snapshots. It creates orders from catalog prices, then coordinates checkout through Kafka events. It does not read another service's database or accept prices from the client.

## API

All endpoints require a valid JWT with a `CUSTOMER` or `ADMIN` role.

### Create an order

`POST /api/orders` requires an `Idempotency-Key` header. Repeating the same request with the same key returns the existing order; reusing that key for a different request returns `409 Conflict`.

```json
{
  "items": [
    { "productId": "catalog-product-id", "quantity": 2 }
  ],
  "paymentMethodId": "pm_test_reference"
}
```

The response is `202 Accepted`. It includes an `orderId`, status, item/price snapshots, total, and timestamps. Payment method references are never returned. A new order starts at `PENDING_INVENTORY`.

### Read, list, and cancel

- `GET /api/orders/{orderId}` returns the caller's order. Admins can read any order.
- `GET /api/orders?page=0&size=20` returns only the caller's orders. Admins may pass `userId` or omit it to list all orders.
- `PATCH /api/orders/{orderId}/cancel` cancels an order while it is awaiting inventory reservation. The cancellation is published so inventory can release any reservation created concurrently.

## Saga events

- `OrderCreated` is written with the order in the database and published to `order-events` through an outbox.
- `InventoryReserved` moves the order to `PENDING_PAYMENT` and emits `PaymentRequested` to `payment-events`, including order total, currency, customer ID, and the provided payment-method reference.
- `InventoryReservationFailed` cancels the order.
- `PaymentCompleted` confirms the order and emits `OrderConfirmed` for inventory to deduct the reservation.
- `PaymentFailed` cancels the order and emits `OrderCancelled` for inventory compensation.
- Order lifecycle events carry the customer's email from the signed JWT so the notification service can deliver confirmation or cancellation messages without reading the auth database.

Outbox rows retry with exponential backoff. Event IDs and order state transitions make duplicate deliveries idempotent.

## Local run

From this directory, run `./gradlew bootRun`. The service uses PostgreSQL `order_db`, Kafka topics `order-events`, `inventory-events`, and `payment-events`, and the catalog HTTP API. `APP_JWT_SECRET` must match the auth service and gateway. The root Docker Compose stack builds and starts this service with the other local services.

```

---

## File: `services/order-service/settings.gradle`

```groovy
rootProject.name = 'order-service'

```

---

## File: `services/order-service/src/main/java/com/ecommerce/order/config/HttpClientConfiguration.java`

```java
package com.ecommerce.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfiguration {
    @Bean
    RestClient catalogRestClient(@Value("${app.catalog.base-url:http://localhost:8081}") String catalogBaseUrl) {
        return RestClient.builder().baseUrl(catalogBaseUrl).build();
    }
}

```

---

## File: `services/order-service/src/main/java/com/ecommerce/order/config/JsonConfiguration.java`

```java
package com.ecommerce.order.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JsonConfiguration {
    @Bean
    ObjectMapper objectMapper() {
        return JsonMapper.builder().findAndAddModules().build();
    }
}

```

---

## File: `services/order-service/src/main/java/com/ecommerce/order/controller/ApiExceptionHandler.java`

```java
package com.ecommerce.order.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> handleInvalidBody(MethodArgumentNotValidException exception) {
        return error(HttpStatus.BAD_REQUEST, "Request validation failed");
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<Map<String, Object>> handleInvalidParameter(HandlerMethodValidationException exception) {
        return error(HttpStatus.BAD_REQUEST, "Request parameters are invalid");
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message
        ));
    }
}

```

---

## File: `services/order-service/src/main/java/com/ecommerce/order/controller/OrderController.java`

```java
package com.ecommerce.order.controller;

import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderPageResponse;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@Validated
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OrderResponse createOrder(Authentication authentication,
                                     @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
                                     @Valid @RequestBody CreateOrderRequest request) {
        Object details = authentication.getDetails();
        if (!(details instanceof String customerEmail) || customerEmail.isBlank()) {
            throw new AuthenticationCredentialsNotFoundException("Customer email claim is required");
        }
        return orderService.createOrder(authentication.getName(), customerEmail, idempotencyKey.trim(), request);
    }

    @GetMapping
    public OrderPageResponse listOrders(Authentication authentication,
                                        @RequestParam(defaultValue = "0") @Min(0) int page,
                                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                        @RequestParam(required = false) String userId) {
        return orderService.listOrders(authentication.getName(), isAdmin(authentication), userId, page, size);
    }

    @GetMapping("/{orderId}")
    public OrderResponse getOrder(Authentication authentication, @PathVariable String orderId) {
        return orderService.getOrder(orderId, authentication.getName(), isAdmin(authentication));
    }

    @PatchMapping("/{orderId}/cancel")
    public OrderResponse cancelOrder(Authentication authentication, @PathVariable String orderId) {
        return orderService.cancelOrder(orderId, authentication.getName(), isAdmin(authentication));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }
}

```

---

## File: `services/order-service/src/main/java/com/ecommerce/order/dto/CatalogProductResponse.java`

```java
package com.ecommerce.order.dto;

import java.math.BigDecimal;

public record CatalogProductResponse(String id, String name, BigDecimal price) {
}

```

---

## File: `services/order-service/src/main/java/com/ecommerce/order/dto/CreateOrderRequest.java`

```java
package com.ecommerce.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(
        @NotEmpty @Size(max = 50) List<@Valid OrderItemRequest> items,
        @NotBlank @Size(max = 255) String paymentMethodId
) {
}

```

---

## File: `services/order-service/src/main/java/com/ecommerce/order/dto/OrderItemRequest.java`

```java
package com.ecommerce.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OrderItemRequest(
        @NotBlank String productId,
        @NotNull @Min(1) @Max(1000) Integer quantity
) {
}

```

---

## File: `services/order-service/src/main/java/com/ecommerce/order/dto/OrderItemResponse.java`

```java
package com.ecommerce.order.dto;

import com.ecommerce.order.model.CustomerOrderItem;

import java.math.BigDecimal;

public record OrderItemResponse(
        String productId,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {
    public static OrderItemResponse from(CustomerOrderItem item) {
        return new OrderItemResponse(item.getProductId(), item.getProductName(), item.getQuantity(),
                item.getUnitPrice(), item.getLineTotal());
    }
}

```

---

## File: `services/order-service/src/main/java/com/ecommerce/order/dto/OrderPageResponse.java`

```java
package com.ecommerce.order.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record OrderPageResponse(
        List<OrderResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static OrderPageResponse from(Page<OrderResponse> page) {
        return new OrderPageResponse(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}

```

---

## File: `services/order-service/src/main/java/com/ecommerce/order/dto/OrderResponse.java`

```java
package com.ecommerce.order.dto;

import com.ecommerce.order.model.CustomerOrder;
import com.ecommerce.order.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        String orderId,
        String userId,
        OrderStatus status,
        BigDecimal totalAmount,
        String currency,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrderResponse from(CustomerOrder order) {
        return new OrderResponse(order.getOrderId(), order.getUserId(), order.getStatus(),
                order.getTotalAmount(), order.getCurrency(),
                order.getItems().stream().map(OrderItemResponse::from).toList(),
                order.getCreatedAt(), order.getUpdatedAt());
    }
}

```

---

## File: `services/order-service/src/main/java/com/ecommerce/order/messaging/InventoryOutcomeEvent.java`

```java
package com.ecommerce.order.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InventoryOutcomeEvent(
        String type,
        String eventId,
        Instant occurredAt,
        String orderId,
        List<OrderEventItem> items,
        String reason
) {
}

```

---

## File: `services/order-service/src/main/java/com/ecommerce/order/messaging/OrderEventItem.java`

```java
package com.ecommerce.order.messaging;

public record OrderEventItem(String productId, Integer quantity) {
}

```

---

## File: `services/order-service/src/main/java/com/ecommerce/order/messaging/OrderEvent.java`

```java
package com.ecommerce.order.messaging;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderEvent(
        @JsonAlias("eventType") String type,
        String eventId,
        Instant occurredAt,
        String orderId,
        String userId,
        String customerEmail,
        BigDecimal totalAmount,
        String currency,
        String paymentMethodId,
        List<OrderEventItem> items,
        String reason
) {
}

```

---

## File: `services/order-service/src/main/java/com/ecommerce/order/messaging/OrderEventListener.java`

```java
package com.ecommerce.order.messaging;

import com.ecommerce.order.service.OrderCommandService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {
    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);
    private final ObjectMapper objectMapper;
    private final OrderCommandService orderCommandService;

    public OrderEventListener(ObjectMapper objectMapper, OrderCommandService orderCommandService) {
        this.objectMapper = objectMapper;
        this.orderCommandService = orderCommandService;
    }

    @KafkaListener(topics = "${app.kafka.inventory-topic:inventory-events}",
            groupId = "${spring.kafka.consumer.group-id:order-service-group}")
    public void consumeInventoryEvent(String payload) {
        try {
            InventoryOutcomeEvent event = objectMapper.readValue(payload, InventoryOutcomeEvent.class);
            if (event.type() == null || event.orderId() == null || event.orderId().isBlank()) {
                throw new IllegalArgumentException("Inventory event requires type and orderId");
            }
            orderCommandService.handleInventoryEvent(event.type(), event.orderId(), event.reason());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid inventory event JSON", exception);
        }
    }

    @KafkaListener(topics = "${app.kafka.payment-topic:payment-events}",
            groupId = "${spring.kafka.consumer.group-id:order-service-group}")
    public void consumePaymentEvent(String payload) {
        try {
            PaymentEvent event = objectMapper.readValue(payload, PaymentEvent.class);
            if (event.type() == null || event.orderId() == null || event.orderId().isBlank()) {
                throw new IllegalArgumentException("Payment event requires type and orderId");
            }
            if (!"PaymentCompleted".equals(event.type()) && !"PaymentFailed".equals(event.type())) {
                log.debug("Ignoring unsupported payment event type {}", event.type());
                return;
            }
            orderCommandService.handlePaymentEvent(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid payment event JSON", exception);
        }
    }

}

```

---

## File: `services/order-service/src/main/java/com/ecommerce/order/messaging/OrderOutboxPublisher.java`

```java
package com.ecommerce.order.messaging;

import com.ecommerce.order.model.OrderOutboxMessage;
import com.ecommerce.order.repository.OrderOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class OrderOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OrderOutboxPublisher.class);
    private final OrderOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OrderOutboxPublisher(OrderOutboxRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${app.kafka.outbox-poll-interval:1000}")
    public void publishPending() {
        List<OrderOutboxMessage> pending = outboxRepository.findReadyToPublish(
                Instant.now(), PageRequest.of(0, 25));
        for (OrderOutboxMessage message : pending) {
            try {
                kafkaTemplate.send(message.getTopic(), message.getMessageKey(), message.getPayload())
                        .get(10, TimeUnit.SECONDS);
                message.markPublished();
                outboxRepository.save(message);
            } catch (Exception exception) {
                String error = exception.getMessage() == null
                        ? exception.getClass().getSimpleName() : exception.getMessage();
                message.markAttemptFailed(error);
                outboxRepository.save(message);
                log.warn("Could not publish order outbox message {}", message.getId(), exception);
            }
        }
    }
}

```

---

## File: `services/order-service/src/main/java/com/ecommerce/order/messaging/PaymentEvent.java`

```java
package com.ecommerce.order.messaging;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentEvent(
        @JsonAlias("eventType") String type,
        String eventId,
        Instant occurredAt,
        String orderId,
        BigDecimal amount,
        String currency,
        String providerReference,
        String reason
) {
}

```

---

## File: `services/order-service/src/main/java/com/ecommerce/order/model/CustomerOrderItem.java`

```java
package com.ecommerce.order.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "purchase_order_item")
public class CustomerOrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", referencedColumnName = "order_id", nullable = false)
    private CustomerOrder order;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal lineTotal;

    protected CustomerOrderItem() {
    }

    public CustomerOrderItem(CustomerOrder order, String productId, String productName,
                             Integer quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
        this.order = order;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.lineTotal = lineTotal;
    }

    public String getProductId() { return productId; }
    public String getProductName() { return productName; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getLineTotal() { return lineTotal; }
}

```

---

## File: `services/order-service/src/main/java/com/ecommerce/order/model/CustomerOrder.java`

```java
package com.ecommerce.order.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "purchase_order")
public class CustomerOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true, length = 36)
    private String orderId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "customer_email", length = 254)
    private String customerEmail;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "payment_method_id", nullable = false)
    private String paymentMethodId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CustomerOrderItem> items = new ArrayList<>();

    protected CustomerOrder() {
    }

    public CustomerOrder(String orderId, String userId, String idempotencyKey, String requestHash,
                         OrderStatus status, BigDecimal totalAmount, String currency, String paymentMethodId) {
        this(orderId, userId, null, idempotencyKey, requestHash, status, totalAmount, currency, paymentMethodId);
    }

    public CustomerOrder(String orderId, String userId, String customerEmail, String idempotencyKey,
                         String requestHash, OrderStatus status, BigDecimal totalAmount,
                         String currency, String paymentMethodId) {
        this.orderId = orderId;
        this.userId = userId;
        this.customerEmail = customerEmail;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.status = status;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.paymentMethodId = paymentMethodId;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void addItem(String productId, String productName, int quantity,
                        BigDecimal unitPrice, BigDecimal lineTotal) {
        items.add(new CustomerOrderItem(this, productId, productName, quantity, unitPrice, lineTotal));
    }

    public boolean transition(OrderStatus expected, OrderStatus next) {
        if (status != expected) {
            return false;
        }
        status = next;
        updatedAt = Instant.now();
        return true;
    }

    public String getOrderId() { return orderId; }
    public String getUserId() { return userId; }
    public String getCustomerEmail() { return customerEmail; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCurrency() { return currency; }
    public String getPaymentMethodId() { return paymentMethodId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<CustomerOrderItem> getItems() { return List.copyOf(items); }
}

```

---

## File: `services/order-service/src/main/java/com/ecommerce/order/model/OrderOutboxMessage.java`

```java
package com.ecommerce.order.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "order_outbox")
public class OrderOutboxMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String topic;

    @Column(name = "message_key", nullable = false)
    private String messageKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    protected OrderOutboxMessage() {
    }

    public OrderOutboxMessage(String topic, String messageKey, String payload) {
        this.topic = topic;
        this.messageKey = messageKey;
        this.payload = payload;
    }

    public void markPublished() {
        publishedAt = Instant.now();
        nextAttemptAt = null;
        lastError = null;
    }

    public void markAttemptFailed(String error) {
        attempts++;
        lastError = error.length() > 2000 ? error.substring(0, 2000) : error;
        nextAttemptAt = Instant.now().plusMillis(Math.min(300_000L, 1_000L << Math.min(attempts - 1, 9)));
    }

    public Long getId() { return id; }
    public String getTopic() { return topic; }
    public String getMessageKey() { return messageKey; }
    public String getPayload() { return payload; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
}

```

---

## File: `services/order-service/src/main/java/com/ecommerce/order/model/OrderStatus.java`

```java
package com.ecommerce.order.model;

public enum OrderStatus {
    PENDING_INVENTORY,
    PENDING_PAYMENT,
    CONFIRMED,
    CANCELLED
}

```

---

## File: `services/order-service/src/main/java/com/ecommerce/order/OrderServiceApplication.java`

```java
package com.ecommerce.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}

```

---

## File: `services/order-service/src/main/java/com/ecommerce/order/repository/CustomerOrderRepository.java`

```java
package com.ecommerce.order.repository;

import com.ecommerce.order.model.CustomerOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
    Optional<CustomerOrder> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);

    @EntityGraph(attributePaths = "items")
    Optional<CustomerOrder> findByOrderIdAndUserId(String orderId, String userId);

    @EntityGraph(attributePaths = "items")
    Optional<CustomerOrder> findByOrderId(String orderId);

    Page<CustomerOrder> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Page<CustomerOrder> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

```

---

## File: `services/order-service/src/main/java/com/ecommerce/order/repository/OrderOutboxRepository.java`

```java
package com.ecommerce.order.repository;

import com.ecommerce.order.model.OrderOutboxMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OrderOutboxRepository extends JpaRepository<OrderOutboxMessage, Long> {
    @Query("""
            select message from OrderOutboxMessage message
            where message.publishedAt is null
              and (message.nextAttemptAt is null or message.nextAttemptAt <= :now)
            order by message.createdAt asc, message.id asc
            """)
    List<OrderOutboxMessage> findReadyToPublish(@Param("now") Instant now, Pageable pageable);
}

```

---

## File: `services/order-service/src/main/java/com/ecommerce/order/security/JwtAuthenticationFilter.java`

```java
package com.ecommerce.order.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Set<String> ALLOWED_ROLES = Set.of("CUSTOMER", "ADMIN");
    private final SecretKey signingKey;

    public JwtAuthenticationFilter(@Value("${app.jwt.secret}") String secret) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret must contain at least 32 UTF-8 bytes");
        }
        signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().equals("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.length() <= 7
                || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Bearer token required");
            return;
        }

        final Claims claims;
        final List<SimpleGrantedAuthority> authorities;
        try {
            claims = Jwts.parser().verifyWith(signingKey).build()
                    .parseSignedClaims(authorization.substring(7).trim()).getPayload();
            Object rawRoles = claims.get("roles");
            if (!(rawRoles instanceof Collection<?> roles)
                    || roles.isEmpty()
                    || roles.stream().anyMatch(role -> !(role instanceof String value)
                    || !ALLOWED_ROLES.contains(value))
                    || claims.getSubject() == null || claims.getSubject().isBlank()) {
                reject(response, "Invalid token claims");
                return;
            }
            authorities = roles.stream()
                    .map(String.class::cast)
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
        } catch (JwtException | IllegalArgumentException exception) {
            reject(response, "Invalid bearer token");
            return;
        }
        String email = claims.get("email", String.class);
        if (email == null || email.isBlank() || !email.contains("@")) {
            reject(response, "Invalid token claims");
            return;
        }
        var authentication = new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
        authentication.setDetails(email.trim());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        SecurityContextHolder.clearContext();
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, message);
    }
}

```

---

## File: `services/order-service/src/main/java/com/ecommerce/order/security/OrderSecurityConfiguration.java`

```java
package com.ecommerce.order.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class OrderSecurityConfiguration {
    @Bean
    SecurityFilterChain orderSecurityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter)
            throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/orders")
                        .hasAnyRole("CUSTOMER", "ADMIN")
                        .requestMatchers("/api/orders", "/api/orders/**")
                        .hasAnyRole("CUSTOMER", "ADMIN")
                        .anyRequest().denyAll())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}

```

---

## File: `services/order-service/src/main/java/com/ecommerce/order/service/CatalogClient.java`

```java
package com.ecommerce.order.service;

import com.ecommerce.order.dto.CatalogProductResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CatalogClient {
    private final RestClient restClient;

    public CatalogClient(RestClient catalogRestClient) {
        restClient = catalogRestClient;
    }

    public CatalogProductResponse getProduct(String productId) {
        try {
            CatalogProductResponse product = restClient.get()
                    .uri("/api/products/{productId}", productId)
                    .retrieve()
                    .body(CatalogProductResponse.class);
            if (product == null || product.id() == null || product.name() == null || product.price() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Catalog returned an incomplete product");
            }
            return product;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Product does not exist: " + productId);
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Catalog service could not validate order products", exception);
        } catch (ResourceAccessException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Catalog service is unavailable", exception);
        }
    }
}

```

---

## File: `services/order-service/src/main/java/com/ecommerce/order/service/OrderCommandService.java`

```java
package com.ecommerce.order.service;

import com.ecommerce.order.messaging.OrderEvent;
import com.ecommerce.order.messaging.OrderEventItem;
import com.ecommerce.order.messaging.PaymentEvent;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.model.CustomerOrder;
import com.ecommerce.order.model.OrderOutboxMessage;
import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.repository.CustomerOrderRepository;
import com.ecommerce.order.repository.OrderOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderCommandService {
    private final CustomerOrderRepository orderRepository;
    private final OrderOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final String orderTopic;
    private final String paymentTopic;

    public OrderCommandService(CustomerOrderRepository orderRepository, OrderOutboxRepository outboxRepository,
                               ObjectMapper objectMapper,
                               @Value("${app.kafka.order-topic:order-events}") String orderTopic,
                               @Value("${app.kafka.payment-topic:payment-events}") String paymentTopic) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.orderTopic = orderTopic;
        this.paymentTopic = paymentTopic;
    }

    @Transactional
    public CustomerOrder createOrder(String userId, String key, String requestHash,
                                     String paymentMethodId, List<ResolvedOrderItem> items) {
        return createOrder(userId, null, key, requestHash, paymentMethodId, items);
    }

    @Transactional
    public CustomerOrder createOrder(String userId, String customerEmail, String key, String requestHash,
                                     String paymentMethodId, List<ResolvedOrderItem> items) {
        return orderRepository.findByUserIdAndIdempotencyKey(userId, key)
                .map(existing -> verifyIdempotentReplay(existing, requestHash))
                .orElseGet(() -> createNewOrder(userId, customerEmail, key, requestHash, paymentMethodId, items));
    }

    @Transactional(readOnly = true)
    public Optional<OrderResponse> findIdempotentReplay(String userId, String key, String requestHash) {
        return orderRepository.findByUserIdAndIdempotencyKey(userId, key)
                .map(existing -> OrderResponse.from(verifyIdempotentReplay(existing, requestHash)));
    }

    private CustomerOrder createNewOrder(String userId, String customerEmail, String key, String requestHash,
                                         String paymentMethodId, List<ResolvedOrderItem> items) {
        BigDecimal total = items.stream()
                .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        CustomerOrder order = new CustomerOrder(UUID.randomUUID().toString(), userId, customerEmail, key, requestHash,
                OrderStatus.PENDING_INVENTORY, total, "USD", paymentMethodId);
        List<OrderEventItem> eventItems = items.stream()
                .map(item -> new OrderEventItem(item.productId(), item.quantity())).toList();
        items.forEach(item -> order.addItem(item.productId(), item.productName(), item.quantity(),
                item.unitPrice(), item.unitPrice().multiply(BigDecimal.valueOf(item.quantity()))
                        .setScale(2, java.math.RoundingMode.HALF_UP)));

        try {
            CustomerOrder saved = orderRepository.saveAndFlush(order);
            enqueue(orderTopic, saved.getOrderId(), orderEvent("OrderCreated", saved, eventItems, null));
            return saved;
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An order with this idempotency key was submitted concurrently; retry the request", exception);
        }
    }

    private CustomerOrder verifyIdempotentReplay(CustomerOrder existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Idempotency-Key was already used with a different order request");
        }
        return existing;
    }

    @Transactional
    public CustomerOrder cancelOrder(String orderId, String userId, boolean admin) {
        CustomerOrder order = getForUser(orderId, userId, admin);
        if (!order.transition(OrderStatus.PENDING_INVENTORY, OrderStatus.CANCELLED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only orders awaiting inventory reservation can be cancelled");
        }
        enqueue(orderTopic, order.getOrderId(), orderEvent("OrderCancelled", order, null, "Cancelled by customer"));
        return order;
    }

    @Transactional
    public void handleInventoryEvent(String type, String orderId, String reason) {
        CustomerOrder order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory event references unknown order"));
        if ("InventoryReserved".equals(type)
                && order.transition(OrderStatus.PENDING_INVENTORY, OrderStatus.PENDING_PAYMENT)) {
            enqueue(paymentTopic, order.getOrderId(), new PaymentRequested(
                    "PaymentRequested", UUID.randomUUID().toString(), Instant.now(), order.getOrderId(),
                    order.getUserId(), order.getTotalAmount(), order.getCurrency(), order.getPaymentMethodId()));
        } else if ("InventoryReservationFailed".equals(type)
                && order.transition(OrderStatus.PENDING_INVENTORY, OrderStatus.CANCELLED)) {
            enqueue(orderTopic, order.getOrderId(), orderEvent("OrderCancelled", order,
                    null, safeReason(reason, "Inventory reservation failed")));
        }
    }

    @Transactional
    public void handlePaymentEvent(PaymentEvent event) {
        CustomerOrder order = orderRepository.findByOrderId(event.orderId())
                .orElseThrow(() -> new IllegalArgumentException("Payment event references unknown order"));
        if (event.amount() == null || event.amount().compareTo(order.getTotalAmount()) != 0
                || event.currency() == null || !event.currency().equalsIgnoreCase(order.getCurrency())) {
            throw new IllegalArgumentException("Payment event amount or currency does not match order");
        }

        if ("PaymentCompleted".equals(event.type())
                && order.transition(OrderStatus.PENDING_PAYMENT, OrderStatus.CONFIRMED)) {
            enqueue(orderTopic, order.getOrderId(), orderEvent("OrderConfirmed", order, null, null));
        } else if ("PaymentFailed".equals(event.type())
                && order.transition(OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED)) {
            enqueue(orderTopic, order.getOrderId(), orderEvent("OrderCancelled", order,
                    null, safeReason(event.reason(), "Payment failed")));
        }
    }

    private CustomerOrder getForUser(String orderId, String userId, boolean admin) {
        return (admin ? orderRepository.findByOrderId(orderId)
                : orderRepository.findByOrderIdAndUserId(orderId, userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private OrderEvent orderEvent(String type, CustomerOrder order, List<OrderEventItem> items, String reason) {
        return new OrderEvent(type, UUID.randomUUID().toString(), Instant.now(), order.getOrderId(),
                order.getUserId(), order.getCustomerEmail(), order.getTotalAmount(),
                order.getCurrency(), null, items, reason);
    }

    private String safeReason(String reason, String fallback) {
        if (reason == null || reason.isBlank()) {
            return fallback;
        }
        return reason.length() > 1000 ? reason.substring(0, 1000) : reason;
    }

    private void enqueue(String topic, String key, Object event) {
        try {
            outboxRepository.save(new OrderOutboxMessage(topic, key, objectMapper.writeValueAsString(event)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize order event", exception);
        }
    }

    private record PaymentRequested(String type, String eventId, Instant occurredAt, String orderId,
                                    String userId, BigDecimal amount, String currency,
                                    String paymentMethodId) {
    }
}

```

---

## File: `services/order-service/src/main/java/com/ecommerce/order/service/OrderService.java`

```java
package com.ecommerce.order.service;

import com.ecommerce.order.dto.CatalogProductResponse;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderItemRequest;
import com.ecommerce.order.dto.OrderPageResponse;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.model.CustomerOrder;
import com.ecommerce.order.repository.CustomerOrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@Service
public class OrderService {
    private final CatalogClient catalogClient;
    private final OrderCommandService commandService;
    private final CustomerOrderRepository orderRepository;

    public OrderService(CatalogClient catalogClient, OrderCommandService commandService,
                        CustomerOrderRepository orderRepository) {
        this.catalogClient = catalogClient;
        this.commandService = commandService;
        this.orderRepository = orderRepository;
    }

    public OrderResponse createOrder(String userId, String customerEmail, String idempotencyKey,
                                     CreateOrderRequest request) {
        TreeMap<String, Integer> quantities = normalize(request.items());
        String paymentMethodId = request.paymentMethodId().trim();
        String requestHash = requestHash(quantities, paymentMethodId);
        Optional<OrderResponse> replay = commandService.findIdempotentReplay(userId, idempotencyKey, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }
        Map<String, CatalogProductResponse> products = new TreeMap<>();
        quantities.keySet().forEach(productId -> products.put(productId, catalogClient.getProduct(productId)));

        var resolvedItems = quantities.entrySet().stream().map(entry -> {
            CatalogProductResponse product = products.get(entry.getKey());
            if (product.price().signum() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Catalog returned a non-positive price for product " + entry.getKey());
            }
            return new ResolvedOrderItem(product.id(), product.name(), entry.getValue(),
                    product.price().setScale(2, java.math.RoundingMode.HALF_UP));
        }).toList();
        CustomerOrder order = customerEmail == null
                ? commandService.createOrder(userId, idempotencyKey, requestHash, paymentMethodId, resolvedItems)
                : commandService.createOrder(userId, customerEmail, idempotencyKey, requestHash,
                        paymentMethodId, resolvedItems);
        return OrderResponse.from(order);
    }

    public OrderResponse createOrder(String userId, String idempotencyKey, CreateOrderRequest request) {
        return createOrder(userId, null, idempotencyKey, request);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(String orderId, String userId, boolean admin) {
        CustomerOrder order = (admin ? orderRepository.findByOrderId(orderId)
                : orderRepository.findByOrderIdAndUserId(orderId, userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public OrderPageResponse listOrders(String userId, boolean admin, String requestedUserId,
                                        int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CustomerOrder> orders;
        if (admin && requestedUserId != null && !requestedUserId.isBlank()) {
            orders = orderRepository.findByUserIdOrderByCreatedAtDesc(requestedUserId, pageable);
        } else if (admin) {
            orders = orderRepository.findAllByOrderByCreatedAtDesc(pageable);
        } else {
            orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }
        return OrderPageResponse.from(orders.map(OrderResponse::from));
    }

    public OrderResponse cancelOrder(String orderId, String userId, boolean admin) {
        return OrderResponse.from(commandService.cancelOrder(orderId, userId, admin));
    }

    private TreeMap<String, Integer> normalize(java.util.List<OrderItemRequest> items) {
        if (items == null || items.isEmpty() || items.size() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An order must contain 1 to 50 items");
        }
        TreeMap<String, Integer> quantities = new TreeMap<>();
        try {
            for (OrderItemRequest item : items) {
                if (item == null || item.productId() == null || item.productId().isBlank()
                        || item.quantity() == null || item.quantity() < 1 || item.quantity() > 1000) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid order item");
                }
                quantities.merge(item.productId().trim(), item.quantity(), Math::addExact);
            }
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order quantity is too large", exception);
        }
        if (quantities.size() > 50 || quantities.values().stream().anyMatch(quantity -> quantity > 1000)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order quantity is too large");
        }
        return quantities;
    }

    private String requestHash(Map<String, Integer> quantities, String paymentMethodId) {
        String canonical = quantities.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(java.util.stream.Collectors.joining("|")) + "|" + paymentMethodId;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}

```

---

## File: `services/order-service/src/main/java/com/ecommerce/order/service/ResolvedOrderItem.java`

```java
package com.ecommerce.order.service;

import java.math.BigDecimal;

public record ResolvedOrderItem(String productId, String productName, int quantity, BigDecimal unitPrice) {
}

```

---

## File: `services/order-service/src/main/resources/application.properties`

```properties
spring.application.name=${SPRING_APPLICATION_NAME:order-service}
server.port=${SERVER_PORT:8080}

spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/order_db}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME:ecommerce}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:ecommerce_dev_password}
spring.jpa.hibernate.ddl-auto=${SPRING_JPA_HIBERNATE_DDL_AUTO:validate}
spring.jpa.show-sql=${SPRING_JPA_SHOW_SQL:false}
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.flyway.enabled=${SPRING_FLYWAY_ENABLED:true}

app.jwt.secret=${APP_JWT_SECRET:local-development-secret-change-this-to-a-long-random-value}
app.catalog.base-url=${CATALOG_SERVICE_URL:http://localhost:8081}
app.kafka.order-topic=${KAFKA_ORDER_TOPIC:order-events}
app.kafka.inventory-topic=${KAFKA_INVENTORY_TOPIC:inventory-events}
app.kafka.payment-topic=${KAFKA_PAYMENT_TOPIC:payment-events}
app.kafka.outbox-poll-interval=${KAFKA_OUTBOX_POLL_INTERVAL:1000}

spring.kafka.bootstrap-servers=${SPRING_KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
spring.kafka.consumer.group-id=${SPRING_KAFKA_CONSUMER_GROUP_ID:order-service-group}
spring.kafka.consumer.auto-offset-reset=${SPRING_KAFKA_CONSUMER_AUTO_OFFSET_RESET:earliest}
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer

management.endpoints.web.exposure.include=${MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE:health,info}
management.endpoint.health.show-details=${MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS:always}
spring.jackson.default-property-inclusion=non_null

```

---

## File: `services/order-service/src/main/resources/db/migration/V1__create_order_schema.sql`

```sql
CREATE TABLE purchase_order (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL UNIQUE,
    user_id VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_amount NUMERIC(19, 2) NOT NULL CHECK (total_amount >= 0),
    currency CHAR(3) NOT NULL,
    payment_method_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT purchase_order_user_idempotency_unique UNIQUE (user_id, idempotency_key)
);

CREATE INDEX purchase_order_user_created_idx ON purchase_order(user_id, created_at DESC);

CREATE TABLE purchase_order_item (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL REFERENCES purchase_order(order_id) ON DELETE CASCADE,
    product_id VARCHAR(255) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(19, 2) NOT NULL CHECK (unit_price > 0),
    line_total NUMERIC(19, 2) NOT NULL CHECK (line_total > 0),
    CONSTRAINT purchase_order_item_product_unique UNIQUE (order_id, product_id)
);

CREATE INDEX purchase_order_item_order_idx ON purchase_order_item(order_id);

CREATE TABLE order_outbox (
    id BIGSERIAL PRIMARY KEY,
    topic VARCHAR(255) NOT NULL,
    message_key VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(2000)
);

CREATE INDEX order_outbox_ready_idx ON order_outbox(next_attempt_at, created_at, id)
    WHERE published_at IS NULL;

```

---

## File: `services/order-service/src/main/resources/db/migration/V2__use_varchar_for_currency.sql`

```sql
ALTER TABLE purchase_order
    ALTER COLUMN currency TYPE VARCHAR(3);

```

---

## File: `services/order-service/src/main/resources/db/migration/V3__use_varchar_for_request_hash.sql`

```sql
ALTER TABLE purchase_order
    ALTER COLUMN request_hash TYPE VARCHAR(64);

```

---

## File: `services/order-service/src/main/resources/db/migration/V4__add_customer_email_for_notifications.sql`

```sql
ALTER TABLE purchase_order
    ADD COLUMN customer_email VARCHAR(254);

```

---

## File: `services/order-service/src/test/java/com/ecommerce/order/OrderServiceApplicationTests.java`

```java
package com.ecommerce.order;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class OrderServiceApplicationTests {
    @Test
    void contextLoads() {
    }
}

```

---

## File: `services/order-service/src/test/java/com/ecommerce/order/service/OrderCommandServiceTest.java`

```java
package com.ecommerce.order.service;

import com.ecommerce.order.messaging.PaymentEvent;
import com.ecommerce.order.model.CustomerOrder;
import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.repository.CustomerOrderRepository;
import com.ecommerce.order.repository.OrderOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCommandServiceTest {
    @Mock
    private CustomerOrderRepository orderRepository;
    @Mock
    private OrderOutboxRepository outboxRepository;

    private OrderCommandService service;

    @BeforeEach
    void setUp() {
        service = new OrderCommandService(orderRepository, outboxRepository,
                new ObjectMapper().findAndRegisterModules(), "order-events", "payment-events");
    }

    @Test
    void createsOrderAndAtomicallyQueuesInventoryEvent() throws Exception {
        when(orderRepository.findByUserIdAndIdempotencyKey("user-1", "checkout-1"))
                .thenReturn(Optional.empty());
        when(orderRepository.saveAndFlush(any(CustomerOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CustomerOrder order = service.createOrder("user-1", "customer@example.com", "checkout-1", "a".repeat(64),
                "pm_test", List.of(new ResolvedOrderItem("p-1", "Shoes", 2, new BigDecimal("12.50"))));

        assertEquals(OrderStatus.PENDING_INVENTORY, order.getStatus());
        assertEquals(new BigDecimal("25.00"), order.getTotalAmount());
        ArgumentCaptor<com.ecommerce.order.model.OrderOutboxMessage> captor =
                ArgumentCaptor.forClass(com.ecommerce.order.model.OrderOutboxMessage.class);
        verify(outboxRepository).save(captor.capture());
        var event = new ObjectMapper().readTree(captor.getValue().getPayload());
        assertEquals("OrderCreated", event.get("type").asText());
        assertEquals(order.getOrderId(), event.get("orderId").asText());
        assertEquals("customer@example.com", event.get("customerEmail").asText());
        assertEquals(2, event.get("items").get(0).get("quantity").asInt());
    }

    @Test
    void rejectsIdempotencyKeyReusedForDifferentRequest() {
        CustomerOrder prior = new CustomerOrder("order-1", "user-1", "checkout-1", "b".repeat(64),
                OrderStatus.PENDING_INVENTORY, new BigDecimal("10.00"), "USD", "pm_test");
        when(orderRepository.findByUserIdAndIdempotencyKey("user-1", "checkout-1"))
                .thenReturn(Optional.of(prior));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.createOrder("user-1", "checkout-1", "a".repeat(64), "pm_test", List.of()));

        assertEquals(409, exception.getStatusCode().value());
    }

    @Test
    void inventorySuccessMovesOrderToPaymentAndQueuesPaymentRequest() {
        CustomerOrder order = new CustomerOrder("order-1", "user-1", "checkout-1", "a".repeat(64),
                OrderStatus.PENDING_INVENTORY, new BigDecimal("19.99"), "USD", "pm_test");
        when(orderRepository.findByOrderId("order-1")).thenReturn(Optional.of(order));

        service.handleInventoryEvent("InventoryReserved", "order-1", null);

        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        ArgumentCaptor<com.ecommerce.order.model.OrderOutboxMessage> captor =
                ArgumentCaptor.forClass(com.ecommerce.order.model.OrderOutboxMessage.class);
        verify(outboxRepository).save(captor.capture());
        assertEquals("payment-events", captor.getValue().getTopic());
    }

    @Test
    void paymentSuccessConfirmsOrderAndQueuesInventoryCommit() throws Exception {
        CustomerOrder order = new CustomerOrder("order-1", "user-1", "customer@example.com",
                "checkout-1", "a".repeat(64), OrderStatus.PENDING_PAYMENT,
                new BigDecimal("19.99"), "USD", "pm_test");
        when(orderRepository.findByOrderId("order-1")).thenReturn(Optional.of(order));

        service.handlePaymentEvent(new PaymentEvent("PaymentCompleted", "event-1", null, "order-1",
                new BigDecimal("19.99"), "USD", "pi_test", null));

        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        ArgumentCaptor<com.ecommerce.order.model.OrderOutboxMessage> captor =
                ArgumentCaptor.forClass(com.ecommerce.order.model.OrderOutboxMessage.class);
        verify(outboxRepository).save(captor.capture());
        assertEquals("order-events", captor.getValue().getTopic());
        assertEquals("customer@example.com",
                new ObjectMapper().readTree(captor.getValue().getPayload()).get("customerEmail").asText());
    }
}

```

---

## File: `services/order-service/src/test/java/com/ecommerce/order/service/OrderOutboxMessageTest.java`

```java
package com.ecommerce.order.service;

import com.ecommerce.order.model.OrderOutboxMessage;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderOutboxMessageTest {
    @Test
    void retryDelayGrowsAfterPublishFailure() {
        OrderOutboxMessage message = new OrderOutboxMessage("order-events", "order-1", "{}");

        Instant firstFailure = Instant.now();
        message.markAttemptFailed("broker unavailable");
        assertTrue(Duration.between(firstFailure, message.getNextAttemptAt()).toMillis() >= 1000);

        Instant secondFailure = Instant.now();
        message.markAttemptFailed("broker unavailable");
        assertTrue(Duration.between(secondFailure, message.getNextAttemptAt()).toMillis() >= 2000);
    }
}

```

---

## File: `services/order-service/src/test/java/com/ecommerce/order/service/OrderServiceTest.java`

```java
package com.ecommerce.order.service;

import com.ecommerce.order.dto.CatalogProductResponse;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderItemRequest;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.model.CustomerOrder;
import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.repository.CustomerOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock
    private CatalogClient catalogClient;
    @Mock
    private OrderCommandService commandService;
    @Mock
    private CustomerOrderRepository orderRepository;

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(catalogClient, commandService, orderRepository);
    }

    @Test
    void usesCatalogPriceAndCombinesDuplicateProducts() {
        when(commandService.findIdempotentReplay(eq("user-1"), eq("checkout-1"), any(String.class)))
                .thenReturn(Optional.empty());
        when(catalogClient.getProduct("product-1"))
                .thenReturn(new CatalogProductResponse("product-1", "Shoes", new BigDecimal("12.50")));
        CustomerOrder saved = new CustomerOrder("order-1", "user-1", "checkout-1", "a".repeat(64),
                OrderStatus.PENDING_INVENTORY, new BigDecimal("37.50"), "USD", "pm_test");
        saved.addItem("product-1", "Shoes", 3, new BigDecimal("12.50"), new BigDecimal("37.50"));
        when(commandService.createOrder(any(), any(), any(), any(), anyList())).thenReturn(saved);

        OrderResponse result = service.createOrder("user-1", "checkout-1", new CreateOrderRequest(
                List.of(new OrderItemRequest("product-1", 1), new OrderItemRequest("product-1", 2)),
                "pm_test"));

        assertEquals(new BigDecimal("37.50"), result.totalAmount());
        assertEquals(3, result.items().getFirst().quantity());
        verify(commandService).createOrder(any(), any(), any(), any(), argThat(items ->
                items.size() == 1 && items.getFirst().quantity() == 3
                        && items.getFirst().unitPrice().equals(new BigDecimal("12.50"))));
    }

    @Test
    void idempotentReplayDoesNotDependOnCatalogAvailability() {
        OrderResponse prior = new OrderResponse("order-1", "user-1", OrderStatus.PENDING_PAYMENT,
                new BigDecimal("10.00"), "USD", List.of(), null, null);
        when(commandService.findIdempotentReplay(eq("user-1"), eq("checkout-1"), any(String.class)))
                .thenReturn(Optional.of(prior));

        OrderResponse result = service.createOrder("user-1", "checkout-1", new CreateOrderRequest(
                List.of(new OrderItemRequest("deleted-product", 1)), "pm_test"));

        assertEquals("order-1", result.orderId());
        verify(catalogClient, never()).getProduct("deleted-product");
        verify(commandService, never()).createOrder(any(), any(), any(), any(), anyList());
    }
}

```

---

## File: `services/order-service/src/test/resources/application.properties`

```properties
spring.datasource.url=jdbc:h2:mem:orders;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.flyway.enabled=false
app.jwt.secret=test-only-order-jwt-secret-longer-than-32-bytes
spring.kafka.listener.auto-startup=false

```

---

## File: `services/payment-service/alembic.ini`

```properties
[alembic]
script_location = migrations
prepend_sys_path = .
sqlalchemy.url = postgresql+psycopg://ecommerce:ecommerce_dev_password@localhost:5432/payment_db

[loggers]
keys = root,sqlalchemy,alembic

[handlers]
keys = console

[formatters]
keys = generic

[logger_root]
level = WARN
handlers = console
qualname =

[logger_sqlalchemy]
level = WARN
handlers =
qualname = sqlalchemy.engine

[logger_alembic]
level = INFO
handlers =
qualname = alembic

[handler_console]
class = StreamHandler
args = (sys.stderr,)
level = NOTSET
formatter = generic

[formatter_generic]
format = %(levelname)-5.5s [%(name)s] %(message)s
datefmt = %H:%M:%S

```

---

## File: `services/payment-service/app/auth.py`

```python
import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from .config import settings

bearer_scheme = HTTPBearer(auto_error=False)
ALLOWED_ROLES = {"CUSTOMER", "ADMIN"}


def current_actor(
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer_scheme),
) -> dict[str, object]:
    if credentials is None or credentials.scheme.lower() != "bearer":
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Bearer token required")
    try:
        claims = jwt.decode(credentials.credentials, settings.jwt_secret, algorithms=["HS256"])
    except jwt.PyJWTError as exc:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid bearer token") from exc

    user_id = claims.get("sub")
    roles = claims.get("roles")
    if (not isinstance(user_id, str) or not user_id.strip()
            or not isinstance(roles, list) or not roles
            or any(not isinstance(role, str) or role not in ALLOWED_ROLES for role in roles)):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token claims")
    return {"user_id": user_id, "roles": roles}

```

---

## File: `services/payment-service/app/config.py`

```python
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

```

---

## File: `services/payment-service/app/database.py`

```python
from sqlalchemy import create_engine
from sqlalchemy.orm import DeclarativeBase, sessionmaker

from .config import settings


class Base(DeclarativeBase):
    pass


engine = create_engine(settings.database_url, pool_pre_ping=True)
SessionLocal = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)

```

---

## File: `services/payment-service/app/__init__.py`

```python


```

---

## File: `services/payment-service/app/kafka_runtime.py`

```python
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

```

---

## File: `services/payment-service/app/main.py`

```python
import logging
from contextlib import asynccontextmanager

import stripe
from fastapi import Depends, FastAPI, HTTPException, Request, Response
from sqlalchemy import text

from .auth import current_actor
from .config import settings
from .database import SessionLocal
from .kafka_runtime import KafkaRuntime
from .processor import PaymentProcessor, WebhookPaymentNotFound
from .schemas import PaymentResponse
from .stripe_gateway import StripeGateway

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

processor = PaymentProcessor(SessionLocal, StripeGateway())
kafka_runtime = KafkaRuntime(processor)


@asynccontextmanager
async def lifespan(_app: FastAPI):
    kafka_runtime.start()
    yield
    kafka_runtime.stop()


app = FastAPI(title="Payment Service", version="1.0.0", lifespan=lifespan)


@app.get("/health")
def health() -> dict[str, str]:
    try:
        with SessionLocal() as session:
            session.execute(text("select 1"))
    except Exception as exc:
        logger.exception("Payment database health check failed")
        raise HTTPException(status_code=503, detail="Payment database is unavailable") from exc
    return {"status": "UP"}


@app.get("/api/payments/orders/{order_id}", response_model=PaymentResponse)
def get_payment(order_id: str, response: Response, actor: dict = Depends(current_actor)):
    payment = processor.get_payment(order_id, actor["user_id"], "ADMIN" in actor["roles"])
    if payment is None:
        raise HTTPException(status_code=404, detail="Payment not found")
    response.headers["Cache-Control"] = "no-store"
    return PaymentResponse(
        order_id=payment.order_id,
        status=payment.status,
        amount=payment.amount,
        currency=payment.currency,
        client_secret=payment.client_secret,
        failure_reason=payment.failure_reason,
        created_at=payment.created_at,
    )


@app.post("/api/payments/webhooks/stripe")
async def stripe_webhook(request: Request) -> dict[str, bool]:
    if not settings.stripe_webhook_secret:
        raise HTTPException(status_code=503, detail="Stripe webhook is not configured")
    signature = request.headers.get("stripe-signature")
    if not signature:
        raise HTTPException(status_code=400, detail="Missing Stripe signature")
    payload = await request.body()
    try:
        event = stripe.Webhook.construct_event(payload, signature, settings.stripe_webhook_secret)
    except (ValueError, stripe.SignatureVerificationError) as exc:
        raise HTTPException(status_code=400, detail="Invalid Stripe webhook") from exc
    if str(event.get("type", "")).startswith("payment_intent."):
        try:
            processor.handle_stripe_webhook(event)
        except WebhookPaymentNotFound as exc:
            raise HTTPException(status_code=503, detail="Payment is not ready for this webhook") from exc
        except ValueError as exc:
            logger.warning("Rejected Stripe webhook: %s", exc)
            raise HTTPException(status_code=400, detail="Invalid payment webhook data") from exc
    return {"received": True}

```

---

## File: `services/payment-service/app/models.py`

```python
from datetime import datetime, timezone

from decimal import Decimal

from sqlalchemy import BigInteger, CheckConstraint, DateTime, Index, Integer, Numeric, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from .database import Base


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


class Payment(Base):
    __tablename__ = "payments"
    __table_args__ = (
        CheckConstraint("amount > 0", name="payments_amount_positive"),
        Index("payments_user_created_idx", "user_id", "created_at"),
    )

    order_id: Mapped[str] = mapped_column(String(36), primary_key=True)
    user_id: Mapped[str] = mapped_column(String(255), nullable=False)
    amount: Mapped[Decimal] = mapped_column(Numeric(19, 2), nullable=False)
    currency: Mapped[str] = mapped_column(String(3), nullable=False)
    payment_method_id: Mapped[str] = mapped_column(String(255), nullable=False)
    stripe_payment_intent_id: Mapped[str | None] = mapped_column(String(255), unique=True)
    client_secret: Mapped[str | None] = mapped_column(String(512))
    status: Mapped[str] = mapped_column(String(32), nullable=False)
    failure_reason: Mapped[str | None] = mapped_column(String(1000))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now,
                                                 onupdate=utc_now, nullable=False)


class StripeWebhookEvent(Base):
    __tablename__ = "stripe_webhook_events"

    event_id: Mapped[str] = mapped_column(String(255), primary_key=True)
    received_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)


class PaymentOutbox(Base):
    __tablename__ = "payment_outbox"
    __table_args__ = (Index("payment_outbox_ready_idx", "next_attempt_at", "created_at", "id"),)

    id: Mapped[int] = mapped_column(
        BigInteger().with_variant(Integer, "sqlite"), primary_key=True, autoincrement=True
    )
    topic: Mapped[str] = mapped_column(String(255), nullable=False)
    message_key: Mapped[str] = mapped_column(String(255), nullable=False)
    payload: Mapped[str] = mapped_column(Text, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)
    published_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    next_attempt_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    attempts: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    last_error: Mapped[str | None] = mapped_column(String(2000))

    def mark_published(self) -> None:
        self.published_at = utc_now()
        self.next_attempt_at = None
        self.last_error = None

    def mark_failed(self, error: str) -> None:
        self.attempts += 1
        self.last_error = error[:2000]
        delay_seconds = min(300, 2 ** min(self.attempts - 1, 8))
        from datetime import timedelta

        self.next_attempt_at = utc_now() + timedelta(seconds=delay_seconds)

```

---

## File: `services/payment-service/app/processor.py`

```python
import uuid
from datetime import datetime, timezone
from decimal import Decimal, ROUND_HALF_UP
from typing import Any

import simplejson
import stripe
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session, sessionmaker

from .config import settings
from .models import Payment, PaymentOutbox, StripeWebhookEvent, utc_now
from .schemas import PaymentRequested
from .stripe_gateway import StripeGateway, StripeNotConfigured

FINAL_STATUSES = {"SUCCEEDED", "FAILED"}
ZERO_DECIMAL_CURRENCIES = {"BIF", "CLP", "DJF", "GNF", "JPY", "KMF", "KRW", "MGA", "PYG", "RWF", "UGX", "VND", "VUV", "XAF", "XOF", "XPF"}
THREE_DECIMAL_CURRENCIES = {"BHD", "JOD", "KWD", "OMR", "TND"}


class WebhookPaymentNotFound(Exception):
    pass


def currency_minor_units(amount: Decimal, currency: str) -> int:
    currency = currency.upper()
    places = 0 if currency in ZERO_DECIMAL_CURRENCIES else 3 if currency in THREE_DECIMAL_CURRENCIES else 2
    multiplier = Decimal(10) ** places
    return int((amount * multiplier).quantize(Decimal("1"), rounding=ROUND_HALF_UP))


def _event_payload(event_type: str, payment: Payment, reason: str | None = None) -> str:
    event = {
        "type": event_type,
        "eventId": str(uuid.uuid4()),
        "occurredAt": datetime.now(timezone.utc).isoformat(),
        "orderId": payment.order_id,
        "amount": payment.amount,
        "currency": payment.currency,
        "providerReference": payment.stripe_payment_intent_id,
        "reason": reason,
    }
    return simplejson.dumps(event, use_decimal=True, ignore_nan=True)


class PaymentProcessor:
    def __init__(self, session_factory: sessionmaker[Session],
                 stripe_gateway: StripeGateway | Any,
                 payment_topic: str | None = None):
        self.session_factory = session_factory
        self.stripe_gateway = stripe_gateway
        self.payment_topic = payment_topic or settings.payment_topic

    def process_payment_request(self, request: PaymentRequested) -> Payment | None:
        with self.session_factory() as session:
            existing = session.get(Payment, request.order_id)
            if existing is not None:
                return existing

        try:
            intent = self.stripe_gateway.create_payment_intent(
                order_id=request.order_id,
                amount=request.amount,
                currency=request.currency,
                payment_method_id=request.payment_method_id,
            )
            status, reason = self._map_intent(intent.status)
            intent_id = intent.payment_intent_id
            client_secret = intent.client_secret
        except StripeNotConfigured as exc:
            status, reason, intent_id, client_secret = "FAILED", str(exc), None, None
        except (stripe.APIConnectionError, stripe.APIError, stripe.RateLimitError):
            # Retry through Kafka with the same Stripe idempotency key after transient provider errors.
            raise
        except stripe.StripeError as exc:
            user_message = getattr(exc, "user_message", None)
            status, reason, intent_id, client_secret = (
                "FAILED", user_message or "The payment provider could not process this payment", None, None
            )

        payment = Payment(
            order_id=request.order_id,
            user_id=request.user_id,
            amount=request.amount,
            currency=request.currency,
            payment_method_id=request.payment_method_id,
            stripe_payment_intent_id=intent_id,
            client_secret=client_secret,
            status=status,
            failure_reason=(reason[:1000] if reason else None),
            created_at=utc_now(),
            updated_at=utc_now(),
        )
        try:
            with self.session_factory.begin() as session:
                session.add(payment)
                if status == "SUCCEEDED":
                    session.add(self._outbox(payment, "PaymentCompleted"))
                elif status == "FAILED":
                    session.add(self._outbox(payment, "PaymentFailed", payment.failure_reason))
        except IntegrityError:
            # Stripe's order-scoped idempotency key makes retrying a concurrent insert safe.
            with self.session_factory() as session:
                existing = session.get(Payment, request.order_id)
                if existing is not None:
                    return existing
            raise
        return payment

    def handle_stripe_webhook(self, event: Any) -> bool:
        event_id = _field(event, "id")
        event_type = _field(event, "type")
        intent = _field(_field(event, "data"), "object")
        intent_id = _field(intent, "id")
        if not event_id or not event_type or not intent_id:
            raise ValueError("Stripe webhook is missing required event fields")

        with self.session_factory.begin() as session:
            if session.get(StripeWebhookEvent, event_id) is not None:
                return False
            payment = session.scalar(
                select(Payment).where(Payment.stripe_payment_intent_id == intent_id).with_for_update()
            )
            if payment is None:
                # Stripe retries 5xx responses; do not mark an early webhook as consumed.
                raise WebhookPaymentNotFound(intent_id)

            self._validate_webhook_amount(intent, payment)
            session.add(StripeWebhookEvent(event_id=event_id, received_at=utc_now()))

            if event_type == "payment_intent.succeeded" and payment.status not in FINAL_STATUSES:
                payment.status = "SUCCEEDED"
                payment.failure_reason = None
                payment.updated_at = utc_now()
                session.add(self._outbox(payment, "PaymentCompleted"))
            elif event_type in {"payment_intent.payment_failed", "payment_intent.canceled"} \
                    and payment.status not in FINAL_STATUSES:
                payment.status = "FAILED"
                error = _field(_field(intent, "last_payment_error"), "message")
                payment.failure_reason = (error or "Payment was not completed")[:1000]
                payment.updated_at = utc_now()
                session.add(self._outbox(payment, "PaymentFailed", payment.failure_reason))
        return True

    def get_payment(self, order_id: str, user_id: str, admin: bool) -> Payment | None:
        with self.session_factory() as session:
            payment = session.get(Payment, order_id)
            if payment is None or (not admin and payment.user_id != user_id):
                return None
            return payment

    def _validate_webhook_amount(self, intent: Any, payment: Payment) -> None:
        amount = _field(intent, "amount_received") or _field(intent, "amount")
        currency = _field(intent, "currency")
        if amount is None or currency is None:
            raise ValueError("Stripe webhook is missing amount or currency")
        if int(amount) != currency_minor_units(payment.amount, payment.currency) \
                or str(currency).upper() != payment.currency:
            raise ValueError("Stripe webhook amount or currency does not match payment")

    def _outbox(self, payment: Payment, event_type: str, reason: str | None = None) -> PaymentOutbox:
        return PaymentOutbox(
            topic=self.payment_topic,
            message_key=payment.order_id,
            payload=_event_payload(event_type, payment, reason),
            created_at=utc_now(),
            attempts=0,
        )

    def _map_intent(self, intent_status: str) -> tuple[str, str | None]:
        if intent_status == "succeeded":
            return "SUCCEEDED", None
        if intent_status in {"requires_action", "processing"}:
            return intent_status.upper(), None
        return "FAILED", "The payment method was declined or the payment was canceled"


def _field(value: Any, name: str) -> Any:
    if value is None:
        return None
    if isinstance(value, dict):
        return value.get(name)
    return getattr(value, name, None)

```

---

## File: `services/payment-service/app/schemas.py`

```python
from datetime import datetime
from decimal import Decimal
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator


class PaymentRequested(BaseModel):
    model_config = ConfigDict(extra="ignore")

    type: Literal["PaymentRequested"]
    event_id: str = Field(alias="eventId", min_length=1, max_length=255)
    order_id: str = Field(alias="orderId", min_length=1, max_length=36)
    user_id: str = Field(alias="userId", min_length=1, max_length=255)
    amount: Decimal = Field(gt=0, max_digits=19, decimal_places=2)
    currency: str = Field(min_length=3, max_length=3)
    payment_method_id: str = Field(alias="paymentMethodId", min_length=1, max_length=255)

    @field_validator("currency")
    @classmethod
    def uppercase_currency(cls, value: str) -> str:
        return value.upper()


class PaymentResponse(BaseModel):
    order_id: str = Field(serialization_alias="orderId")
    status: str
    amount: Decimal
    currency: str
    client_secret: str | None = Field(default=None, serialization_alias="clientSecret")
    failure_reason: str | None = Field(default=None, serialization_alias="failureReason")
    created_at: datetime = Field(serialization_alias="createdAt")


```

---

## File: `services/payment-service/app/stripe_gateway.py`

```python
from dataclasses import dataclass
from decimal import Decimal, ROUND_HALF_UP

import stripe

from .config import settings


class StripeNotConfigured(Exception):
    pass


@dataclass(frozen=True)
class StripeIntentResult:
    payment_intent_id: str
    status: str
    client_secret: str | None


class StripeGateway:
    def create_payment_intent(self, *, order_id: str, amount: Decimal, currency: str,
                              payment_method_id: str) -> StripeIntentResult:
        if not settings.stripe_secret_key:
            raise StripeNotConfigured("Stripe payments are not configured")

        minor_units = int((amount * 100).quantize(Decimal("1"), rounding=ROUND_HALF_UP))
        stripe.api_key = settings.stripe_secret_key
        intent = stripe.PaymentIntent.create(
            amount=minor_units,
            currency=currency.lower(),
            payment_method=payment_method_id,
            confirm=True,
            automatic_payment_methods={"enabled": True, "allow_redirects": "never"},
            metadata={"orderId": order_id},
            idempotency_key=f"order-payment-{order_id}",
        )
        return StripeIntentResult(intent.id, intent.status, intent.client_secret)


```

---

## File: `services/payment-service/migrations/env.py`

```python
from alembic import context
from sqlalchemy import engine_from_config, pool

from app.config import settings
from app.models import Base

config = context.config
config.set_main_option("sqlalchemy.url", settings.database_url)
target_metadata = Base.metadata


def run_migrations_offline() -> None:
    context.configure(
        url=settings.database_url,
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
        compare_type=True,
    )
    with context.begin_transaction():
        context.run_migrations()


def run_migrations_online() -> None:
    connectable = engine_from_config(
        config.get_section(config.config_ini_section, {}),
        prefix="sqlalchemy.",
        poolclass=pool.NullPool,
    )
    with connectable.connect() as connection:
        context.configure(connection=connection, target_metadata=target_metadata, compare_type=True)
        with context.begin_transaction():
            context.run_migrations()


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()

```

---

## File: `services/payment-service/migrations/versions/0001_create_payment_schema.py`

```python
"""Create payment records, webhook deduplication, and transactional outbox."""

from alembic import op
import sqlalchemy as sa

revision = "0001_payment_schema"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "payments",
        sa.Column("order_id", sa.String(length=36), primary_key=True),
        sa.Column("user_id", sa.String(length=255), nullable=False),
        sa.Column("amount", sa.Numeric(19, 2), nullable=False),
        sa.Column("currency", sa.String(length=3), nullable=False),
        sa.Column("payment_method_id", sa.String(length=255), nullable=False),
        sa.Column("stripe_payment_intent_id", sa.String(length=255), nullable=True, unique=True),
        sa.Column("client_secret", sa.String(length=512), nullable=True),
        sa.Column("status", sa.String(length=32), nullable=False),
        sa.Column("failure_reason", sa.String(length=1000), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.CheckConstraint("amount > 0", name="payments_amount_positive"),
    )
    op.create_index("payments_user_created_idx", "payments", ["user_id", "created_at"])

    op.create_table(
        "stripe_webhook_events",
        sa.Column("event_id", sa.String(length=255), primary_key=True),
        sa.Column("received_at", sa.DateTime(timezone=True), nullable=False),
    )

    op.create_table(
        "payment_outbox",
        sa.Column("id", sa.BigInteger(), primary_key=True, autoincrement=True),
        sa.Column("topic", sa.String(length=255), nullable=False),
        sa.Column("message_key", sa.String(length=255), nullable=False),
        sa.Column("payload", sa.Text(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("published_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("next_attempt_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("attempts", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("last_error", sa.String(length=2000), nullable=True),
    )
    op.create_index(
        "payment_outbox_ready_idx",
        "payment_outbox",
        ["next_attempt_at", "created_at", "id"],
        postgresql_where=sa.text("published_at IS NULL"),
    )


def downgrade() -> None:
    op.drop_index("payment_outbox_ready_idx", table_name="payment_outbox")
    op.drop_table("payment_outbox")
    op.drop_table("stripe_webhook_events")
    op.drop_index("payments_user_created_idx", table_name="payments")
    op.drop_table("payments")

```

---

## File: `services/payment-service/.pytest_cache/README.md`

```markdown
# pytest cache directory #

This directory contains data from the pytest's cache plugin,
which provides the `--lf` and `--ff` options, as well as the `cache` fixture.

**Do not** commit this to version control.

See [the docs](https://docs.pytest.org/en/stable/how-to/cache.html) for more information.

```

---

## File: `services/payment-service/README.md`

```markdown
# Payment Service

The payment service consumes `PaymentRequested` messages from `payment-events`, creates an idempotent Stripe PaymentIntent, and stores each attempt in `payment_db`. Successful or failed attempts are published to the same Kafka topic through a transactional outbox; the order service consumes `PaymentCompleted` and `PaymentFailed` from that topic. Stripe webhook events update asynchronous payment outcomes and are deduplicated by Stripe event ID.

## Configuration

Compose reads database, Kafka, and JWT settings from the root `.env`. Set `STRIPE_SECRET_KEY` and `STRIPE_WEBHOOK_SECRET` to Stripe test-mode values to process real test payments and verify webhook signatures. With no Stripe secret key, requested payments are recorded as failed and a `PaymentFailed` event is published; no charge is attempted.

The internal API listens on port `8000` and is exposed locally as `http://localhost:8002`. Through the gateway, `GET /api/payments/orders/{orderId}` returns payment status for the owning customer or an admin. Stripe sends signed callbacks to `POST /api/payments/webhooks/stripe`; configure the webhook endpoint at `/api/payments/webhooks/stripe` on the gateway or forward it locally to port `8002`.

## Local checks

```sh
python -m pip install -r requirements-dev.txt
pytest -q
```

```

---

## File: `services/payment-service/tests/test_processor.py`

```python
import json
from decimal import Decimal

import pytest
from sqlalchemy import create_engine, select
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from app.database import Base
from app.models import Payment, PaymentOutbox, StripeWebhookEvent
from app.processor import PaymentProcessor
from app.schemas import PaymentRequested
from app.stripe_gateway import StripeIntentResult, StripeNotConfigured


@pytest.fixture
def session_factory():
    engine = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    Base.metadata.create_all(engine)
    yield sessionmaker(bind=engine, expire_on_commit=False)
    Base.metadata.drop_all(engine)
    engine.dispose()


class FakeStripe:
    def __init__(self, status="succeeded", raises=None):
        self.status = status
        self.raises = raises
        self.calls = 0

    def create_payment_intent(self, **_kwargs):
        self.calls += 1
        if self.raises:
            raise self.raises
        return StripeIntentResult("pi_test_123", self.status, "pi_test_123_secret" if self.status == "requires_action" else None)


def request():
    return PaymentRequested.model_validate({
        "type": "PaymentRequested",
        "eventId": "evt-request-1",
        "orderId": "order-123",
        "userId": "user-456",
        "amount": "42.50",
        "currency": "USD",
        "paymentMethodId": "pm_test_123",
    })


def test_successful_payment_persists_and_emits_completion(session_factory):
    processor = PaymentProcessor(session_factory, FakeStripe())

    payment = processor.process_payment_request(request())

    assert payment.status == "SUCCEEDED"
    with session_factory() as session:
        event = session.scalar(select(PaymentOutbox))
        payload = json.loads(event.payload)
        assert event.topic == "payment-events"
        assert event.message_key == "order-123"
        assert payload["type"] == "PaymentCompleted"
        assert payload["amount"] == 42.5
        assert payload["providerReference"] == "pi_test_123"


def test_duplicate_request_is_idempotent(session_factory):
    stripe_gateway = FakeStripe()
    processor = PaymentProcessor(session_factory, stripe_gateway)

    first = processor.process_payment_request(request())
    second = processor.process_payment_request(request())

    assert first.order_id == second.order_id
    assert stripe_gateway.calls == 1
    with session_factory() as session:
        assert len(list(session.scalars(select(PaymentOutbox)))) == 1


def test_missing_stripe_configuration_emits_failure(session_factory):
    processor = PaymentProcessor(session_factory, FakeStripe(raises=StripeNotConfigured("Stripe is not configured")))

    payment = processor.process_payment_request(request())

    assert payment.status == "FAILED"
    with session_factory() as session:
        event = session.scalar(select(PaymentOutbox))
        assert json.loads(event.payload)["type"] == "PaymentFailed"


def test_requires_action_waits_for_webhook_and_deduplicates_event(session_factory):
    processor = PaymentProcessor(session_factory, FakeStripe(status="requires_action"))
    payment = processor.process_payment_request(request())
    assert payment.status == "REQUIRES_ACTION"
    assert payment.client_secret == "pi_test_123_secret"

    event = {
        "id": "evt-stripe-success",
        "type": "payment_intent.succeeded",
        "data": {"object": {
            "id": "pi_test_123",
            "amount_received": 4250,
            "currency": "usd",
        }},
    }
    assert processor.handle_stripe_webhook(event) is True
    assert processor.handle_stripe_webhook(event) is False

    with session_factory() as session:
        saved_payment = session.get(Payment, "order-123")
        assert saved_payment.status == "SUCCEEDED"
        assert len(list(session.scalars(select(PaymentOutbox)))) == 1
        assert session.get(StripeWebhookEvent, "evt-stripe-success") is not None


def test_webhook_rejects_mismatched_amount(session_factory):
    processor = PaymentProcessor(session_factory, FakeStripe(status="requires_action"))
    processor.process_payment_request(request())
    event = {
        "id": "evt-stripe-bad",
        "type": "payment_intent.succeeded",
        "data": {"object": {"id": "pi_test_123", "amount_received": 1, "currency": "usd"}},
    }

    with pytest.raises(ValueError, match="does not match"):
        processor.handle_stripe_webhook(event)

```
