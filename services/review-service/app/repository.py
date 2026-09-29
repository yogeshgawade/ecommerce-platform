from datetime import datetime, timezone
from math import ceil
from typing import Any

from pymongo import ASCENDING, DESCENDING, ReturnDocument


class ReviewRepository:
    def __init__(self, reviews: Any):
        self.reviews = reviews

    async def ensure_indexes(self) -> None:
        await self.reviews.create_index(
            [("userId", ASCENDING), ("productId", ASCENDING)],
            unique=True,
            name="uq_review_user_product",
        )
        await self.reviews.create_index(
            [("productId", ASCENDING), ("createdAt", DESCENDING)],
            name="ix_review_product_created_at",
        )

    async def create(self, *, user_id: str, product_id: str, order_id: str,
                     rating: int, title: str, body: str) -> dict[str, Any]:
        now = datetime.now(timezone.utc)
        document = {
            "userId": user_id,
            "productId": product_id,
            "orderId": order_id,
            "rating": rating,
            "title": title,
            "body": body,
            "verifiedPurchase": True,
            "createdAt": now,
            "updatedAt": now,
        }
        result = await self.reviews.insert_one(document)
        document["_id"] = result.inserted_id
        return document

    async def update(self, *, user_id: str, product_id: str,
                     rating: int, title: str, body: str) -> dict[str, Any] | None:
        return await self.reviews.find_one_and_update(
            {"userId": user_id, "productId": product_id},
            {"$set": {
                "rating": rating,
                "title": title,
                "body": body,
                "updatedAt": datetime.now(timezone.utc),
            }},
            return_document=ReturnDocument.AFTER,
        )

    async def delete(self, *, user_id: str, product_id: str) -> bool:
        result = await self.reviews.delete_one({"userId": user_id, "productId": product_id})
        return result.deleted_count > 0

    async def get_page(self, product_id: str, page: int, size: int) -> tuple[list[dict[str, Any]], int, float | None]:
        query = {"productId": product_id}
        total = await self.reviews.count_documents(query)
        cursor = (
            self.reviews.find(query)
            .sort("createdAt", DESCENDING)
            .skip(page * size)
            .limit(size)
        )
        reviews = await cursor.to_list(length=size)
        summary_cursor = await self.reviews.aggregate([
            {"$match": query},
            {
                "$group": {
                    "_id": None,
                    "averageRating": {"$avg": "$rating"},
                    "reviewCount": {"$sum": 1},
                }
            },
        ])
        summary = await summary_cursor.to_list(length=1)
        average = round(float(summary[0]["averageRating"]), 2) if summary else None
        return reviews, total, average

    async def get_summary(self, product_id: str) -> tuple[float | None, int]:
        summary_cursor = await self.reviews.aggregate([
            {"$match": {"productId": product_id}},
            {
                "$group": {
                    "_id": None,
                    "averageRating": {"$avg": "$rating"},
                    "reviewCount": {"$sum": 1},
                }
            },
        ])
        summary = await summary_cursor.to_list(length=1)
        if not summary:
            return None, 0
        return round(float(summary[0]["averageRating"]), 2), int(summary[0]["reviewCount"])

    @staticmethod
    def to_response(document: dict[str, Any]) -> dict[str, Any]:
        return {
            "review_id": str(document["_id"]),
            "product_id": document["productId"],
            "rating": document["rating"],
            "title": document["title"],
            "body": document["body"],
            "verified_purchase": document["verifiedPurchase"],
            "created_at": document["createdAt"],
            "updated_at": document["updatedAt"],
        }

    @staticmethod
    def total_pages(total: int, size: int) -> int:
        return ceil(total / size) if total else 0
