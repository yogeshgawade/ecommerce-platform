from datetime import datetime, timezone
from math import ceil
from typing import Any

from pymongo import ASCENDING, DESCENDING, ReturnDocument
from pymongo.errors import DuplicateKeyError


class WishlistRepository:
    def __init__(self, collection: Any):
        self.collection = collection

    async def ensure_indexes(self) -> None:
        await self.collection.create_index(
            [("userId", ASCENDING), ("productId", ASCENDING)],
            unique=True,
            name="uq_wishlist_user_product",
        )
        await self.collection.create_index(
            [("userId", ASCENDING), ("addedAt", DESCENDING)],
            name="ix_wishlist_user_added_at",
        )

    async def add_item(self, user_id: str, product_id: str) -> dict[str, Any]:
        try:
            item = await self.collection.find_one_and_update(
                {"userId": user_id, "productId": product_id},
                {"$setOnInsert": {
                    "userId": user_id,
                    "productId": product_id,
                    "addedAt": datetime.now(timezone.utc),
                }},
                upsert=True,
                return_document=ReturnDocument.AFTER,
            )
        except DuplicateKeyError:
            # Another request may insert this same product after the upsert check.
            item = await self.collection.find_one({"userId": user_id, "productId": product_id})
            if item is None:
                raise
        return item

    async def list_items(self, user_id: str, page: int, size: int) -> tuple[list[dict[str, Any]], int]:
        total = await self.collection.count_documents({"userId": user_id})
        cursor = (
            self.collection.find({"userId": user_id})
            .sort("addedAt", DESCENDING)
            .skip(page * size)
            .limit(size)
        )
        items = await cursor.to_list(length=size)
        return items, total

    async def remove_item(self, user_id: str, product_id: str) -> None:
        await self.collection.delete_one({"userId": user_id, "productId": product_id})

    async def clear(self, user_id: str) -> None:
        await self.collection.delete_many({"userId": user_id})

    @staticmethod
    def total_pages(total: int, size: int) -> int:
        return ceil(total / size) if total else 0
