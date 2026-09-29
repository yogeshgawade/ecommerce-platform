from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field, field_validator


class AddWishlistItemRequest(BaseModel):
    product_id: str = Field(min_length=1, max_length=200)

    @field_validator("product_id")
    @classmethod
    def trim_product_id(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise ValueError("product_id must not be blank")
        return normalized


class WishlistItemResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    product_id: str
    added_at: datetime


class WishlistResponse(BaseModel):
    user_id: str
    items: list[WishlistItemResponse] = Field(default_factory=list)
    page: int
    size: int
    total_elements: int
    total_pages: int
