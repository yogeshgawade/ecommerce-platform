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
