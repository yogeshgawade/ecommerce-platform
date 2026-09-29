from typing import Any

from app.schemas import ProductEvent
from app.search_index import SearchIndex


def event(event_type: str, *, attributes: dict[str, str] | None = None) -> ProductEvent:
    payload: dict[str, Any] = {
        "eventId": "event-1",
        "eventType": event_type,
        "productId": "product-1",
    }
    if event_type != "ProductDeleted":
        payload["product"] = {
            "name": "Trail Runner",
            "description": "Lightweight running shoe",
            "category": "Footwear",
            "brand": "Acme",
            "price": 89.99,
            "attributes": attributes,
        }
    return ProductEvent.model_validate(payload)


class FakeIndices:
    def __init__(self, has_alias: bool = False):
        self.has_alias = has_alias
        self.create_calls: list[dict[str, Any]] = []

    def exists_alias(self, *, name: str) -> bool:
        return self.has_alias

    def create(self, **kwargs: Any) -> None:
        self.create_calls.append(kwargs)
        self.has_alias = True


class FakeElasticsearch:
    def __init__(self, search_response: dict[str, Any] | None = None):
        self.indices = FakeIndices()
        self.index_calls: list[dict[str, Any]] = []
        self.update_calls: list[dict[str, Any]] = []
        self.delete_calls: list[dict[str, Any]] = []
        self.search_calls: list[dict[str, Any]] = []
        self.search_response = search_response or {"hits": {"hits": [], "total": {"value": 0}}}

    def index(self, **kwargs: Any) -> None:
        self.index_calls.append(kwargs)

    def update(self, **kwargs: Any) -> None:
        self.update_calls.append(kwargs)

    def delete(self, **kwargs: Any) -> None:
        self.delete_calls.append(kwargs)

    def search(self, **kwargs: Any) -> dict[str, Any]:
        self.search_calls.append(kwargs)
        return self.search_response


def test_ensure_index_creates_alias_and_product_mapping():
    client = FakeElasticsearch()
    search = SearchIndex(client, alias="products")

    search.ensure_index()
    search.ensure_index()

    assert len(client.indices.create_calls) == 1
    created = client.indices.create_calls[0]
    assert created["index"] == "products-v1"
    assert created["aliases"] == {"products": {}}
    assert created["mappings"]["properties"]["category"]["type"] == "keyword"
    assert created["mappings"]["properties"]["price"]["type"] == "scaled_float"


def test_created_and_updated_events_index_by_product_id():
    client = FakeElasticsearch()
    search = SearchIndex(client, alias="products")

    search.apply_event(event("ProductCreated", attributes={"color": "blue"}))
    search.apply_event(event("ProductUpdated", attributes={"color": "red"}))

    assert [call["id"] for call in client.update_calls] == ["product-1", "product-1"]
    assert client.update_calls[0]["doc"]["productId"] == "product-1"
    assert client.update_calls[0]["doc"]["attributeText"] == "color blue"
    assert client.update_calls[1]["doc"]["attributeText"] == "color red"
    assert all(call["index"] == "products" for call in client.update_calls)
    assert all(call["doc_as_upsert"] is True for call in client.update_calls)


def test_missing_attributes_are_indexed_as_empty():
    client = FakeElasticsearch()
    search = SearchIndex(client, alias="products")

    search.apply_event(event("ProductCreated"))

    assert client.update_calls[0]["doc"]["attributes"] == {}
    assert client.update_calls[0]["doc"]["attributeText"] == ""


def test_deleted_event_removes_product_document():
    client = FakeElasticsearch()
    search = SearchIndex(client, alias="products")

    search.apply_event(event("ProductDeleted"))

    assert client.delete_calls == [{"index": "products", "id": "product-1", "refresh": "wait_for"}]
    assert client.index_calls == []
    assert client.update_calls == []


def test_unknown_event_type_is_ignored():
    client = FakeElasticsearch()
    search = SearchIndex(client, alias="products")

    search.apply_event(event("ProductArchived"))

    assert client.index_calls == []
    assert client.update_calls == []
    assert client.delete_calls == []


def test_search_builds_query_filters_and_returns_pagination_and_facets():
    response = {
        "hits": {
            "hits": [{"_source": {"productId": "product-1", "name": "Trail Runner"}, "_score": 4.2}],
            "total": {"value": 21, "relation": "eq"},
        },
        "aggregations": {
            "categories": {"buckets": [{"key": "Footwear", "doc_count": 12}]},
            "brands": {"buckets": [{"key": "Acme", "doc_count": 7}]},
            "prices": {"buckets": [{"key": "50_to_100", "from": 50.0, "to": 100.0, "doc_count": 5}]},
            "ratings": {"buckets": [{"key": 4.5, "doc_count": 3}]},
        },
    }
    client = FakeElasticsearch(search_response=response)
    search = SearchIndex(client, alias="products")

    result = search.search(
        query="trail runner",
        category="Footwear",
        brand="Acme",
        min_price=40,
        max_price=120,
        min_rating=4,
        page=2,
        size=10,
        sort="price_asc",
    )

    request = client.search_calls[0]
    assert request["from_"] == 20
    assert request["size"] == 10
    assert request["sort"] == [{"price": "asc"}, {"name.keyword": "asc"}]
    filters = request["query"]["bool"]["filter"]
    assert {"term": {"category": {"value": "Footwear", "case_insensitive": True}}} in filters
    assert {"term": {"brand": {"value": "Acme", "case_insensitive": True}}} in filters
    assert {"range": {"price": {"gte": 40, "lte": 120}}} in filters
    assert {"range": {"rating": {"gte": 4}}} in filters
    assert result["totalElements"] == 21
    assert result["totalPages"] == 3
    assert result["content"][0]["score"] == 4.2
    assert result["facets"]["categories"] == [{"value": "Footwear", "count": 12}]
    assert result["facets"]["brands"] == [{"value": "Acme", "count": 7}]
    assert result["facets"]["prices"][0]["key"] == "50_to_100"
    assert result["facets"]["ratings"] == [{"value": "4.5", "count": 3}]


def test_empty_query_uses_match_all_and_zero_results_have_zero_pages():
    client = FakeElasticsearch({"hits": {"hits": [], "total": {"value": 0}}})
    search = SearchIndex(client, alias="products")

    result = search.search(
        query=None,
        category=None,
        brand=None,
        min_price=None,
        max_price=None,
        min_rating=None,
        page=0,
        size=20,
        sort="relevance",
    )

    assert client.search_calls[0]["query"]["bool"]["must"] == [{"match_all": {}}]
    assert result["totalElements"] == 0
    assert result["totalPages"] == 0
    assert result["content"] == []
