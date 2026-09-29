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
