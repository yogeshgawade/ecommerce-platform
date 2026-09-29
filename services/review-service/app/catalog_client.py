from urllib.parse import quote

import httpx
from fastapi import HTTPException, status
from pydantic import BaseModel, Field, ValidationError

from .config import settings


class CatalogProduct(BaseModel):
    id: str
    name: str = Field(min_length=1)


class CatalogClient:
    def __init__(self, client: httpx.AsyncClient, base_url: str = settings.catalog_service_url):
        self.client = client
        self.base_url = base_url.rstrip("/")

    async def ensure_product_exists(self, product_id: str) -> None:
        try:
            response = await self.client.get(
                f"{self.base_url}/api/products/{quote(product_id, safe='')}"
            )
        except httpx.RequestError as exc:
            raise HTTPException(status_code=503, detail="Catalog service is unavailable") from exc

        if response.status_code == status.HTTP_404_NOT_FOUND:
            raise HTTPException(status_code=404, detail="Product not found")
        if response.status_code >= 500:
            raise HTTPException(status_code=503, detail="Catalog service is unavailable")
        if response.status_code != status.HTTP_200_OK:
            raise HTTPException(status_code=502, detail="Catalog service returned an unexpected response")

        try:
            product = CatalogProduct.model_validate(response.json())
        except (ValueError, ValidationError) as exc:
            raise HTTPException(status_code=502, detail="Catalog service returned invalid product data") from exc
        if product.id != product_id:
            raise HTTPException(status_code=502, detail="Catalog service returned a mismatched product")
