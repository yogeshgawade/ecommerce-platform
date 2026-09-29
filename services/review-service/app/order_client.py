from urllib.parse import quote

import httpx
from fastapi import HTTPException
from pydantic import BaseModel, ConfigDict, Field, ValidationError

from .config import settings


class OrderItem(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    product_id: str = Field(alias="productId")


class CustomerOrder(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    order_id: str = Field(alias="orderId")
    user_id: str = Field(alias="userId")
    status: str
    items: list[OrderItem]


class OrderClient:
    def __init__(self, client: httpx.AsyncClient, base_url: str = settings.order_service_url):
        self.client = client
        self.base_url = base_url.rstrip("/")

    async def verify_confirmed_purchase(
        self, *, order_id: str, user_id: str, product_id: str, token: str
    ) -> None:
        try:
            response = await self.client.get(
                f"{self.base_url}/api/orders/{quote(order_id, safe='')}",
                headers={"Authorization": f"Bearer {token}"},
            )
        except httpx.RequestError as exc:
            raise HTTPException(status_code=503, detail="Order service is unavailable") from exc

        if response.status_code == 404:
            raise HTTPException(status_code=404, detail="Confirmed order not found")
        if response.status_code >= 500:
            raise HTTPException(status_code=503, detail="Order service is unavailable")
        if response.status_code != 200:
            raise HTTPException(status_code=502, detail="Order service returned an unexpected response")

        try:
            order = CustomerOrder.model_validate(response.json())
        except (ValueError, ValidationError) as exc:
            raise HTTPException(status_code=502, detail="Order service returned invalid order data") from exc
        if order.order_id != order_id or order.user_id != user_id:
            raise HTTPException(status_code=502, detail="Order service returned mismatched order data")
        if order.status != "CONFIRMED" or product_id not in {item.product_id for item in order.items}:
            raise HTTPException(status_code=403, detail="A confirmed purchase of this product is required")
