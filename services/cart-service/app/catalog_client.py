import os
from decimal import Decimal, InvalidOperation
from urllib.parse import quote

import httpx
from fastapi import HTTPException, status
from pydantic import BaseModel, Field, ValidationError


class CatalogProduct(BaseModel):
    id: str
    name: str = Field(min_length=1)
    price: Decimal = Field(gt=0)


class CatalogClient:
    def __init__(self, base_url: str | None = None):
        self.base_url = (base_url or os.getenv("CATALOG_SERVICE_URL", "http://localhost:8081")).rstrip("/")
        self.client = httpx.AsyncClient(timeout=httpx.Timeout(3.0, connect=1.0))

    async def get_product(self, product_id: str) -> CatalogProduct:
        try:
            response = await self.client.get(f"{self.base_url}/api/products/{quote(product_id, safe='')}")
        except httpx.RequestError as exc:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Catalog service is unavailable",
            ) from exc

        if response.status_code == status.HTTP_404_NOT_FOUND:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Product not found")
        if response.status_code >= 500:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Catalog service is unavailable",
            )
        if response.status_code != status.HTTP_200_OK:
            raise HTTPException(
                status_code=status.HTTP_502_BAD_GATEWAY,
                detail="Catalog service returned an unexpected response",
            )

        try:
            product = CatalogProduct.model_validate(response.json())
        except (ValueError, ValidationError, InvalidOperation) as exc:
            raise HTTPException(
                status_code=status.HTTP_502_BAD_GATEWAY,
                detail="Catalog service returned invalid product data",
            ) from exc
        if product.id != product_id:
            raise HTTPException(
                status_code=status.HTTP_502_BAD_GATEWAY,
                detail="Catalog service returned a mismatched product",
            )
        return product

    async def close(self) -> None:
        await self.client.aclose()


catalog_client = CatalogClient()
