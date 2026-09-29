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

