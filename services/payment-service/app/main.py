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
