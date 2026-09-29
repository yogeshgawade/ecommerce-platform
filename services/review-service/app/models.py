from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field, field_validator


class CreateReviewRequest(BaseModel):
    order_id: str = Field(min_length=1, max_length=128)
    rating: int = Field(ge=1, le=5)
    title: str = Field(min_length=1, max_length=120)
    body: str = Field(min_length=1, max_length=3000)

    @field_validator("order_id", "title", "body")
    @classmethod
    def trim_required_text(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise ValueError("value must not be blank")
        return normalized


class UpdateReviewRequest(BaseModel):
    rating: int = Field(ge=1, le=5)
    title: str = Field(min_length=1, max_length=120)
    body: str = Field(min_length=1, max_length=3000)

    @field_validator("title", "body")
    @classmethod
    def trim_required_text(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise ValueError("value must not be blank")
        return normalized


class ReviewResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    review_id: str
    product_id: str
    rating: int
    title: str
    body: str
    verified_purchase: bool
    created_at: datetime
    updated_at: datetime


class ReviewSummary(BaseModel):
    average_rating: float | None
    review_count: int


class ReviewPageResponse(BaseModel):
    content: list[ReviewResponse]
    page: int
    size: int
    total_elements: int
    total_pages: int
    average_rating: float | None
    review_count: int
