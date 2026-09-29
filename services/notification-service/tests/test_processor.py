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
