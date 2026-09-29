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
