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
