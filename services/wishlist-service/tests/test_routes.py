from datetime import datetime, timezone

import pytest
from fastapi import HTTPException

from app.main import add_to_wishlist, clear_wishlist, get_wishlist, remove_from_wishlist, app


class FakeRepository:
    def __init__(self):
        self.list_result = ([], 0)
        self.add_result = {
            "productId": "product-1",
            "addedAt": datetime(2026, 1, 1, tzinfo=timezone.utc),
        }
        self.list_calls = []
        self.add_calls = []
        self.remove_calls = []
        self.clear_calls = []

    async def list_items(self, user_id, page, size):
        self.list_calls.append((user_id, page, size))
        return self.list_result

    async def add_item(self, user_id, product_id):
        self.add_calls.append((user_id, product_id))
        return self.add_result

    async def remove_item(self, user_id, product_id):
        self.remove_calls.append((user_id, product_id))

    async def clear(self, user_id):
        self.clear_calls.append(user_id)

    @staticmethod
    def total_pages(total, size):
        return (total + size - 1) // size if total else 0


class FakeCatalogClient:
    def __init__(self):
        self.product_ids = []
        self.error = None

    async def ensure_product_exists(self, product_id):
        self.product_ids.append(product_id)
        if self.error:
            raise self.error


def configure_app(repository=None, catalog=None):
    app.state.wishlist_repository = repository or FakeRepository()
    app.state.catalog_client = catalog or FakeCatalogClient()


@pytest.mark.asyncio
async def test_get_wishlist_scopes_query_to_verified_user_and_returns_page():
    repository = FakeRepository()
    repository.list_result = ([{
        "productId": "product-1",
        "addedAt": datetime(2026, 1, 1, tzinfo=timezone.utc),
    }], 1)
    configure_app(repository=repository)
    response = await get_wishlist(user_id="customer-1", page=0, size=5)

    assert response.user_id == "customer-1"
    assert response.items[0].product_id == "product-1"
    assert response.total_elements == 1
    assert repository.list_calls == [("customer-1", 0, 5)]


@pytest.mark.asyncio
async def test_add_wishlist_item_checks_catalog_and_uses_verified_user():
    repository = FakeRepository()
    catalog = FakeCatalogClient()
    configure_app(repository=repository, catalog=catalog)
    response = await add_to_wishlist(product_id="product-1", user_id="customer-1")

    assert response.product_id == "product-1"
    assert catalog.product_ids == ["product-1"]
    assert repository.add_calls == [("customer-1", "product-1")]


@pytest.mark.asyncio
async def test_add_wishlist_item_returns_catalog_not_found():
    catalog = FakeCatalogClient()
    catalog.error = HTTPException(status_code=404, detail="Product not found")
    configure_app(catalog=catalog)

    with pytest.raises(HTTPException) as error:
        await add_to_wishlist(product_id="missing", user_id="customer-1")
    assert error.value.status_code == 404


@pytest.mark.asyncio
async def test_remove_wishlist_item_uses_verified_user_and_returns_no_content():
    repository = FakeRepository()
    configure_app(repository=repository)
    response = await remove_from_wishlist(product_id="product-1", user_id="customer-1")

    assert response.status_code == 204
    assert repository.remove_calls == [("customer-1", "product-1")]


@pytest.mark.asyncio
async def test_clear_wishlist_only_clears_verified_user():
    repository = FakeRepository()
    configure_app(repository=repository)
    response = await clear_wishlist(user_id="customer-1")

    assert response.status_code == 204
    assert repository.clear_calls == ["customer-1"]
