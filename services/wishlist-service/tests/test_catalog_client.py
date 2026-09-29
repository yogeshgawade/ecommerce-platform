import httpx
import pytest
from fastapi import HTTPException

from app.catalog_client import CatalogClient


def client_for(response: httpx.Response | Exception) -> CatalogClient:
    def handler(request: httpx.Request) -> httpx.Response:
        if isinstance(response, Exception):
            raise response
        return response

    http_client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    return CatalogClient("http://catalog.test/", client=http_client)


@pytest.mark.asyncio
async def test_ensure_product_exists_requests_encoded_product_id_and_accepts_product():
    requests: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        return httpx.Response(200, json={"id": "item/1", "name": "Product", "price": 25})

    catalog = CatalogClient(
        "http://catalog.test",
        client=httpx.AsyncClient(transport=httpx.MockTransport(handler)),
    )
    try:
        await catalog.ensure_product_exists("item/1")
    finally:
        await catalog.close()

    assert requests[0].url.raw_path == b"/api/products/item%2F1"


@pytest.mark.asyncio
async def test_ensure_product_exists_maps_not_found_to_404():
    catalog = client_for(httpx.Response(404))
    try:
        with pytest.raises(HTTPException) as error:
            await catalog.ensure_product_exists("missing")
    finally:
        await catalog.close()

    assert error.value.status_code == 404


@pytest.mark.asyncio
async def test_ensure_product_exists_maps_catalog_failure_to_503():
    catalog = client_for(httpx.Response(503))
    try:
        with pytest.raises(HTTPException) as error:
            await catalog.ensure_product_exists("product-1")
    finally:
        await catalog.close()

    assert error.value.status_code == 503


@pytest.mark.asyncio
async def test_ensure_product_exists_rejects_mismatched_product_id():
    catalog = client_for(httpx.Response(200, json={"id": "different", "name": "Product"}))
    try:
        with pytest.raises(HTTPException) as error:
            await catalog.ensure_product_exists("product-1")
    finally:
        await catalog.close()

    assert error.value.status_code == 502


@pytest.mark.asyncio
async def test_ensure_product_exists_maps_network_failure_to_503():
    failure = httpx.ConnectError("connection refused", request=httpx.Request("GET", "http://catalog.test"))
    catalog = client_for(failure)
    try:
        with pytest.raises(HTTPException) as error:
            await catalog.ensure_product_exists("product-1")
    finally:
        await catalog.close()

    assert error.value.status_code == 503
