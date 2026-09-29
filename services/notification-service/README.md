# Notification Service

The notification service consumes `OrderConfirmed` and `OrderCancelled` events from `order-events`. It deduplicates notifications by event ID and records delivery attempts in its own `notification_db` database.

For local development, the default `NOTIFICATION_EMAIL_BACKEND=log` simulates delivery and records the status as `SIMULATED`; it does not contact an email provider. Set `NOTIFICATION_EMAIL_BACKEND=smtp` and configure `SMTP_HOST`, `SMTP_PORT`, `SMTP_FROM`, and optional SMTP credentials to send email. SMTP failures leave the Kafka message uncommitted so it is retried.

Order events include the customer's email from the signed auth token. The notification worker is internal and has no public API beyond its database-backed `/health` check.

## Local checks

```sh
python -m pip install -r requirements-dev.txt
pytest -q
```
