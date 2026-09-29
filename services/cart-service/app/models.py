from decimal import Decimal

from pydantic import BaseModel, Field


class AddCartItemRequest(BaseModel):
    product_id: str = Field(min_length=1, max_length=200)
    quantity: int = Field(gt=0, le=1000)


class CartItem(BaseModel):
    product_id: str
    quantity: int = Field(gt=0, le=1000)


class CartItemResponse(BaseModel):
    product_id: str
    name: str
    price: Decimal
    quantity: int


class UpdateCartItemRequest(BaseModel):
    quantity: int = Field(gt=0, le=1000)


class Cart(BaseModel):
    user_id: str
    items: list[CartItem] = Field(default_factory=list)


class CartResponse(BaseModel):
    user_id: str
    items: list[CartItemResponse] = Field(default_factory=list)
