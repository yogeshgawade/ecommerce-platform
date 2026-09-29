from typing import Any

from elasticsearch import Elasticsearch, NotFoundError

from .config import settings
from .schemas import ProductEvent


class SearchIndex:
    def __init__(self, client: Elasticsearch, alias: str = settings.elasticsearch_index):
        self.client = client
        self.alias = alias

    def ensure_index(self) -> None:
        if self.client.indices.exists_alias(name=self.alias):
            return
        self.client.indices.create(
            index=f"{self.alias}-v1",
            aliases={self.alias: {}},
            settings={"number_of_shards": 1, "number_of_replicas": 0},
            mappings={
                "properties": {
                    "productId": {"type": "keyword"},
                    "name": {
                        "type": "text",
                        "fields": {"keyword": {"type": "keyword", "ignore_above": 256}},
                    },
                    "description": {"type": "text"},
                    "attributeText": {"type": "text"},
                    "category": {"type": "keyword"},
                    "brand": {"type": "keyword"},
                    "price": {"type": "scaled_float", "scaling_factor": 100},
                    "attributes": {"type": "flattened"},
                    "rating": {"type": "float"},
                    "reviewCount": {"type": "integer"},
                }
            },
        )

    def apply_event(self, event: ProductEvent) -> None:
        if event.event_type == "ProductDeleted":
            try:
                self.client.delete(index=self.alias, id=event.product_id, refresh="wait_for")
            except NotFoundError:
                pass
            return

        if event.event_type not in {"ProductCreated", "ProductUpdated"}:
            return
        if event.product is None:
            raise ValueError(f"{event.event_type} event has no product snapshot")

        product = event.product.model_dump(exclude_none=True)
        attributes = product.get("attributes") or {}
        product["attributes"] = attributes
        product["attributeText"] = " ".join(
            f"{key} {value}" for key, value in attributes.items()
        )
        product["productId"] = event.product_id
        self.client.update(
            index=self.alias,
            id=event.product_id,
            doc=product,
            doc_as_upsert=True,
            refresh="wait_for",
        )

    def apply_rating_event(self, event: Any) -> None:
        if event.event_type != "ReviewRatingUpdated":
            return
        self.client.update(
            index=self.alias,
            id=event.product_id,
            doc={"rating": event.average_rating, "reviewCount": event.review_count},
            doc_as_upsert=True,
            refresh="wait_for",
        )

    def search(
        self,
        *,
        query: str | None,
        category: str | None,
        brand: str | None,
        min_price: float | None,
        max_price: float | None,
        min_rating: float | None,
        page: int,
        size: int,
        sort: str,
    ) -> dict[str, Any]:
        text_query: dict[str, Any] = (
            {"multi_match": {
                "query": query,
                "fields": ["name^3", "brand^2", "category^2", "description", "attributeText"],
                "type": "best_fields",
                "fuzziness": "AUTO",
            }}
            if query
            else {"match_all": {}}
        )

        filters: list[dict[str, Any]] = []
        if category:
            filters.append({"term": {"category": {"value": category, "case_insensitive": True}}})
        if brand:
            filters.append({"term": {"brand": {"value": brand, "case_insensitive": True}}})
        price_range: dict[str, float] = {}
        if min_price is not None:
            price_range["gte"] = min_price
        if max_price is not None:
            price_range["lte"] = max_price
        if price_range:
            filters.append({"range": {"price": price_range}})
        if min_rating is not None:
            filters.append({"range": {"rating": {"gte": min_rating}}})

        sort_options: dict[str, Any] = {
            "relevance": [{"_score": "desc"}, {"name.keyword": "asc"}],
            "price_asc": [{"price": "asc"}, {"name.keyword": "asc"}],
            "price_desc": [{"price": "desc"}, {"name.keyword": "asc"}],
            "name_asc": [{"name.keyword": "asc"}],
            "rating_desc": [{"rating": {"order": "desc", "missing": "_last"}}, {"name.keyword": "asc"}],
        }
        query_body: dict[str, Any] = {
            "bool": {"must": [text_query], "filter": filters}
        }
        result = self.client.search(
            index=self.alias,
            from_=page * size,
            size=size,
            query=query_body,
            sort=sort_options[sort],
            aggs={
                "categories": {"terms": {"field": "category", "size": 30}},
                "brands": {"terms": {"field": "brand", "size": 30}},
                "prices": {
                    "range": {
                        "field": "price",
                        "ranges": [
                            {"key": "under_25", "to": 25},
                            {"key": "25_to_50", "from": 25, "to": 50},
                            {"key": "50_to_100", "from": 50, "to": 100},
                            {"key": "100_to_250", "from": 100, "to": 250},
                            {"key": "250_plus", "from": 250},
                        ],
                    }
                },
                "ratings": {"terms": {"field": "rating", "size": 5, "order": {"_key": "desc"}}},
            },
        )
        hits = result["hits"]["hits"]
        total_value = result["hits"]["total"]
        total = total_value["value"] if isinstance(total_value, dict) else total_value
        pages = (total + size - 1) // size if total else 0
        aggregations = result.get("aggregations", {})
        return {
            "content": [{**hit["_source"], "score": hit.get("_score")} for hit in hits],
            "page": page,
            "size": size,
            "totalElements": total,
            "totalPages": pages,
            "facets": {
                "categories": [
                    {"value": bucket["key"], "count": bucket["doc_count"]}
                    for bucket in aggregations.get("categories", {}).get("buckets", [])
                ],
                "brands": [
                    {"value": bucket["key"], "count": bucket["doc_count"]}
                    for bucket in aggregations.get("brands", {}).get("buckets", [])
                ],
                "prices": [
                    {
                        "key": bucket["key"],
                        "from": bucket.get("from"),
                        "to": bucket.get("to"),
                        "count": bucket["doc_count"],
                    }
                    for bucket in aggregations.get("prices", {}).get("buckets", [])
                ],
                "ratings": [
                    {"value": str(bucket["key"]), "count": bucket["doc_count"]}
                    for bucket in aggregations.get("ratings", {}).get("buckets", [])
                ],
            },
        }
