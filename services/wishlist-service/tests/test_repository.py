from datetime import datetime, timezone

import pytest
from pymongo import ASCENDING, DESCENDING, ReturnDocument
from pymongo.errors import DuplicateKeyError

from app.repository import WishlistRepository


class FakeCursor:
    def __init__(self, documents):
        self.documents = documents
        self.sort_args = None
        self.skip_count = None
        self.limit_count = None

    def sort(self, *args):
        self.sort_args = args
        return self

    def skip(self, count):
        self.skip_count = count
        return self

    def limit(self, count):
        self.limit_count = count
        return self

    async def to_list(self, length):
        return self.documents[:length]


class FakeCollection:
    def __init__(self):
        self.index_calls = []
        self.update_calls = []
        self.documents = []
        self.update_result = None
        self.duplicate_on_update = False
        self.find_one_result = None
        self.cursor = FakeCursor([])
        self.total_count = 0
        self.deleted_one = None
        self.deleted_many = None

    async def create_index(self, keys, **kwargs):
        self.index_calls.append((keys, kwargs))

    async def find_one_and_update(self, query, update, **kwargs):
        self.update_calls.append((query, update, kwargs))
        if self.duplicate_on_update:
            raise DuplicateKeyError("duplicate")
        return self.update_result

    async def find_one(self, query):
        self.find_one_query = query
        return self.find_one_result

    async def count_documents(self, query):
        self.count_query = query
        return self.total_count

    def find(self, query):
        self.find_query = query
        return self.cursor

    async def delete_one(self, query):
        self.deleted_one = query

    async def delete_many(self, query):
        self.deleted_many = query


@pytest.mark.asyncio
async def test_ensure_indexes_creates_unique_user_product_and_list_indexes():
    collection = FakeCollection()
    repository = WishlistRepository(collection)

    await repository.ensure_indexes()

    assert collection.index_calls[0][0] == [("userId", ASCENDING), ("productId", ASCENDING)]
    assert collection.index_calls[0][1]["unique"] is True
    assert collection.index_calls[0][1]["name"] == "uq_wishlist_user_product"
    assert collection.index_calls[1][0] == [("userId", ASCENDING), ("addedAt", DESCENDING)]


@pytest.mark.asyncio
async def test_add_item_upserts_without_overwriting_original_added_time():
    added_at = datetime.now(timezone.utc)
    collection = FakeCollection()
    collection.update_result = {"userId": "user-1", "productId": "product-1", "addedAt": added_at}
    repository = WishlistRepository(collection)

    item = await repository.add_item("user-1", "product-1")

    query, update, options = collection.update_calls[0]
    assert query == {"userId": "user-1", "productId": "product-1"}
    assert set(update) == {"$setOnInsert"}
    assert update["$setOnInsert"]["userId"] == "user-1"
    assert update["$setOnInsert"]["productId"] == "product-1"
    assert options["upsert"] is True
    assert options["return_document"] == ReturnDocument.AFTER
    assert item["addedAt"] == added_at


@pytest.mark.asyncio
async def test_add_item_recovers_concurrent_duplicate_upsert():
    existing = {"userId": "user-1", "productId": "product-1", "addedAt": datetime.now(timezone.utc)}
    collection = FakeCollection()
    collection.duplicate_on_update = True
    collection.find_one_result = existing
    repository = WishlistRepository(collection)

    item = await repository.add_item("user-1", "product-1")

    assert item is existing
    assert collection.find_one_query == {"userId": "user-1", "productId": "product-1"}


@pytest.mark.asyncio
async def test_list_items_filters_by_user_and_applies_pagination():
    collection = FakeCollection()
    collection.total_count = 25
    collection.cursor = FakeCursor([{"productId": "product-21"}])
    repository = WishlistRepository(collection)

    items, total = await repository.list_items("user-1", page=2, size=10)

    assert collection.count_query == {"userId": "user-1"}
    assert collection.find_query == {"userId": "user-1"}
    assert collection.cursor.sort_args == ("addedAt", DESCENDING)
    assert collection.cursor.skip_count == 20
    assert collection.cursor.limit_count == 10
    assert items == [{"productId": "product-21"}]
    assert total == 25


@pytest.mark.asyncio
async def test_remove_and_clear_only_target_calling_users_data():
    collection = FakeCollection()
    repository = WishlistRepository(collection)

    await repository.remove_item("user-1", "product-1")
    await repository.clear("user-1")

    assert collection.deleted_one == {"userId": "user-1", "productId": "product-1"}
    assert collection.deleted_many == {"userId": "user-1"}


@pytest.mark.parametrize(
    ("total", "size", "expected"),
    [(0, 20, 0), (1, 20, 1), (20, 20, 1), (21, 20, 2)],
)
def test_total_pages(total, size, expected):
    assert WishlistRepository.total_pages(total, size) == expected
