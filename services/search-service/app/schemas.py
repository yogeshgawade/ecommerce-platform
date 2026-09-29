from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class ProductSnapshot(BaseModel):
    model_config = ConfigDict(extra="ignore")

    name: str
    description: str | None = None
    category: str
    brand: str
    price: float
    attributes: dict[str, str] | None = None
    rating: float | None = None


class ProductEvent(BaseModel):
    model_config = ConfigDict(extra="ignore", populate_by_name=True)

    event_id: str = Field(alias="eventId")
    event_type: str = Field(alias="eventType")
    product_id: str = Field(alias="productId")
    product: ProductSnapshot | None = None


class ReviewRatingEvent(BaseModel):
    model_config = ConfigDict(extra="ignore", populate_by_name=True)

    event_id: str = Field(alias="eventId")
    event_type: str = Field(alias="eventType")
    product_id: str = Field(alias="productId")
    average_rating: float | None = Field(default=None, alias="averageRating", ge=0, le=5)
    review_count: int = Field(alias="reviewCount", ge=0)


class FacetValue(BaseModel):
    value: str
    count: int


class PriceFacet(BaseModel):
    key: str
    from_price: float | None = Field(default=None, alias="from")
    to_price: float | None = Field(default=None, alias="to")
    count: int


class SearchFacets(BaseModel):
    categories: list[FacetValue] = Field(default_factory=list)
    brands: list[FacetValue] = Field(default_factory=list)
    prices: list[PriceFacet] = Field(default_factory=list)
    ratings: list[FacetValue] = Field(default_factory=list)


class SearchResponse(BaseModel):
    content: list[dict[str, Any]]
    page: int
    size: int
    total_elements: int = Field(alias="totalElements")
    total_pages: int = Field(alias="totalPages")
    facets: SearchFacets
