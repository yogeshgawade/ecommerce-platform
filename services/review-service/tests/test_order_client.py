import httpx
import pytest
from fastapi import HTTPException

from app.order_client import OrderClient


def response(payload, status_code=200):
    return httpx.Response(status_code, json=payload)


@pytest.mark.asyncio
async def test_order_client_forwards_bearer_and_accepts_confirmed_product_purchase():
    requests = []
    order = {
        "orderId": "order-1",
        "userId": "customer-1",
        "status": "CONFIRMED",
        "items": [{"productId": "product-1", "quantity": 1}],
    }

    def handler(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        return response(order)

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        await OrderClient(client, "http://orders").verify_confirmed_purchase(
            order_id="order-1", user_id="customer-1", product_id="product-1", token="jwt-value"
        )

    assert requests[0].headers["Authorization"] == "Bearer jwt-value"
    assert requests[0].url.path == "/api/orders/order-1"


@pytest.mark.parametrize(
    "order",
    [
        {"orderId": "order-1", "userId": "someone-else", "status": "CONFIRMED", "items": [{"productId": "product-1"}]},
        {"orderId": "order-1", "userId": "customer-1", "status": "PENDING", "items": [{"productId": "product-1"}]},
        {"orderId": "order-1", "userId": "customer-1", "status": "CONFIRMED", "items": [{"productId": "another-product"}]},
    ],
)
@pytest.mark.asyncio
async def test_order_client_rejects_unowned_unconfirmed_or_unrelated_order(order):
    transport = httpx.MockTransport(lambda _request: response(order))
    async with httpx.AsyncClient(transport=transport) as client:
        with pytest.raises(HTTPException) as error:
            await OrderClient(client, "http://orders").verify_confirmed_purchase(
                order_id="order-1", user_id="customer-1", product_id="product-1", token="jwt"
            )

    assert error.value.status_code in {403, 502}


@pytest.mark.asyncio
async def test_order_client_maps_not_found_and_unavailable_responses():
    for status_code, expected in [(404, 404), (503, 503)]:
        transport = httpx.MockTransport(lambda _request, code=status_code: response({}, code))
        async with httpx.AsyncClient(transport=transport) as client:
            with pytest.raises(HTTPException) as error:
                await OrderClient(client, "http://orders").verify_confirmed_purchase(
                    order_id="order-1", user_id="customer-1", product_id="product-1", token="jwt"
                )
        assert error.value.status_code == expected
