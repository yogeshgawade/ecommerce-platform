from decimal import Decimal

from pydantic import BaseModel, Field


class CartItem(BaseModel):
    product_id: str = Field(min_length=1)
    name: str = Field(min_length=1)
    price: Decimal = Field(gt=0)
    quantity: int = Field(gt=0)


class UpdateCartItemRequest(BaseModel):
    quantity: int = Field(gt=0)


class Cart(BaseModel):
    user_id: str
    items: list[CartItem] = Field(default_factory=list)
