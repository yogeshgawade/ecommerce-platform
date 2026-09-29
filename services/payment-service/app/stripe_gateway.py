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

