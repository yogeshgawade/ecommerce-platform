import httpx
import pytest
from fastapi import HTTPException

from app.catalog_client import CatalogClient


@pytest.mark.asyncio
async def test_catalog_client_checks_escaped_product_path_and_response():
    requests = []

    def handler(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        return httpx.Response(200, json={"id": "product/1", "name": "Shoe"})

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        await CatalogClient(client, "http://catalog").ensure_product_exists("product/1")

    assert requests[0].url.raw_path == b"/api/products/product%2F1"


@pytest.mark.asyncio
async def test_catalog_client_maps_missing_product_to_404():
    transport = httpx.MockTransport(lambda _request: httpx.Response(404))
    async with httpx.AsyncClient(transport=transport) as client:
        with pytest.raises(HTTPException) as error:
            await CatalogClient(client, "http://catalog").ensure_product_exists("missing")

    assert error.value.status_code == 404


@pytest.mark.asyncio
async def test_catalog_client_maps_upstream_failure_to_503():
    transport = httpx.MockTransport(lambda _request: httpx.Response(503))
    async with httpx.AsyncClient(transport=transport) as client:
        with pytest.raises(HTTPException) as error:
            await CatalogClient(client, "http://catalog").ensure_product_exists("product-1")

    assert error.value.status_code == 503


@pytest.mark.asyncio
async def test_catalog_client_rejects_mismatched_product_id():
    transport = httpx.MockTransport(
        lambda _request: httpx.Response(200, json={"id": "other", "name": "Shoe"})
    )
    async with httpx.AsyncClient(transport=transport) as client:
        with pytest.raises(HTTPException) as error:
            await CatalogClient(client, "http://catalog").ensure_product_exists("product-1")

    assert error.value.status_code == 502
