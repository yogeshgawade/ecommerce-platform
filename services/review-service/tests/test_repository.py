from datetime import datetime, timezone

import pytest
from bson import ObjectId
from pymongo import ASCENDING, DESCENDING, ReturnDocument

from app.repository import ReviewRepository


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
        self.inserted = None
        self.inserted_id = ObjectId()
        self.update_call = None
        self.update_result = None
        self.delete_query = None
        self.deleted_count = 0
        self.total_count = 0
        self.cursor = FakeCursor([])
        self.aggregations = []
        self.aggregate_results = []

    async def create_index(self, keys, **kwargs):
        self.index_calls.append((keys, kwargs))

    async def insert_one(self, document):
        self.inserted = document.copy()

        class Result:
            inserted_id = self.inserted_id

        return Result()

    async def find_one_and_update(self, query, update, **kwargs):
        self.update_call = (query, update, kwargs)
        return self.update_result

    async def delete_one(self, query):
        self.delete_query = query

        class Result:
            deleted_count = self.deleted_count

        return Result()

    async def count_documents(self, query):
        self.count_query = query
        return self.total_count

    def find(self, query):
        self.find_query = query
        return self.cursor

    def aggregate(self, pipeline):
        self.aggregations.append(pipeline)

        class AggregateCursor:
            def __init__(self, results):
                self.results = results

            async def to_list(self, length):
                return self.results[:length]

        return AggregateCursor(self.aggregate_results)


@pytest.mark.asyncio
async def test_ensure_indexes_enforces_one_review_per_user_and_product():
    collection = FakeCollection()
    await ReviewRepository(collection).ensure_indexes()

    assert collection.index_calls[0][0] == [("userId", ASCENDING), ("productId", ASCENDING)]
    assert collection.index_calls[0][1]["unique"] is True
    assert collection.index_calls[0][1]["name"] == "uq_review_user_product"
    assert collection.index_calls[1][0] == [("productId", ASCENDING), ("createdAt", DESCENDING)]


@pytest.mark.asyncio
async def test_create_marks_review_verified_and_sets_timestamps():
    collection = FakeCollection()
    document = await ReviewRepository(collection).create(
        user_id="user-1", product_id="product-1", order_id="order-1",
        rating=5, title="Great", body="Works well",
    )

    assert collection.inserted["verifiedPurchase"] is True
    assert collection.inserted["userId"] == "user-1"
    assert collection.inserted["orderId"] == "order-1"
    assert document["_id"] == collection.inserted_id
    assert document["createdAt"].tzinfo == timezone.utc
    assert document["updatedAt"] == document["createdAt"]


@pytest.mark.asyncio
async def test_update_is_scoped_to_owner_and_product():
    collection = FakeCollection()
    collection.update_result = {"_id": ObjectId(), "rating": 4}
    await ReviewRepository(collection).update(
        user_id="user-1", product_id="product-1", rating=4, title="Good", body="Solid"
    )

    query, update, options = collection.update_call
    assert query == {"userId": "user-1", "productId": "product-1"}
    assert update["$set"]["rating"] == 4
    assert options["return_document"] == ReturnDocument.AFTER
    assert "updatedAt" in update["$set"]


@pytest.mark.asyncio
async def test_delete_is_scoped_to_owner_and_product():
    collection = FakeCollection()
    collection.deleted_count = 1

    deleted = await ReviewRepository(collection).delete(user_id="user-1", product_id="product-1")

    assert deleted is True
    assert collection.delete_query == {"userId": "user-1", "productId": "product-1"}


@pytest.mark.asyncio
async def test_get_page_applies_sort_pagination_and_aggregate():
    review = {"_id": ObjectId(), "productId": "product-1", "rating": 4}
    collection = FakeCollection()
    collection.total_count = 25
    collection.cursor = FakeCursor([review])
    collection.aggregate_results = [{"averageRating": 4.25, "reviewCount": 25}]

    result = await ReviewRepository(collection).get_page("product-1", page=2, size=10)

    assert collection.find_query == {"productId": "product-1"}
    assert collection.cursor.sort_args == ("createdAt", DESCENDING)
    assert collection.cursor.skip_count == 20
    assert collection.cursor.limit_count == 10
    assert result == ([review], 25, 4.25)


@pytest.mark.asyncio
async def test_get_summary_returns_empty_when_no_reviews():
    collection = FakeCollection()

    assert await ReviewRepository(collection).get_summary(product_id="product-1") == (None, 0)


@pytest.mark.parametrize("total,size,expected", [(0, 20, 0), (1, 20, 1), (20, 20, 1), (21, 20, 2)])
def test_total_pages(total, size, expected):
    assert ReviewRepository.total_pages(total, size) == expected


def test_to_response_serializes_review_document():
    now = datetime.now(timezone.utc)
    document = {
        "_id": ObjectId("64a000000000000000000001"),
        "productId": "product-1",
        "rating": 5,
        "title": "Great",
        "body": "Very good",
        "verifiedPurchase": True,
        "createdAt": now,
        "updatedAt": now,
    }

    response = ReviewRepository.to_response(document)

    assert response["review_id"] == str(document["_id"])
    assert response["product_id"] == "product-1"
    assert response["verified_purchase"] is True
