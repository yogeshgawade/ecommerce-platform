from decimal import Decimal
from typing import Literal

from pydantic import BaseModel, ConfigDict, EmailStr, Field


class OrderEvent(BaseModel):
    model_config = ConfigDict(extra="ignore")

    type: Literal["OrderConfirmed", "OrderCancelled"]
    event_id: str = Field(alias="eventId", min_length=1, max_length=255)
    order_id: str = Field(alias="orderId", min_length=1, max_length=36)
    user_id: str | None = Field(default=None, alias="userId", max_length=255)
    customer_email: EmailStr | None = Field(default=None, alias="customerEmail")
    total_amount: Decimal | None = Field(default=None, alias="totalAmount", gt=0)
    currency: str | None = Field(default=None, min_length=3, max_length=3)
    reason: str | None = Field(default=None, max_length=1000)
