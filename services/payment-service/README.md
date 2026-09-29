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
