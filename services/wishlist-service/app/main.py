from contextlib import asynccontextmanager
from typing import Annotated

from fastapi import Depends, FastAPI, HTTPException, Query, Response, status
from pymongo import AsyncMongoClient
from pymongo.errors import PyMongoError

from .auth import authenticated_user_id
from .catalog_client import CatalogClient
from .config import settings
from .models import AddWishlistItemRequest, WishlistItemResponse, WishlistResponse
from .repository import WishlistRepository


@asynccontextmanager
async def lifespan(app: FastAPI):
    client = AsyncMongoClient(settings.mongodb_uri, serverSelectionTimeoutMS=5000, tz_aware=True)
    database = client.get_default_database()
    if database is None:
        raise RuntimeError("MONGODB_URI must include a database name")
    await client.admin.command("ping")
    repository = WishlistRepository(database.get_collection("wishlist_items"))
    await repository.ensure_indexes()
    catalog_client = CatalogClient()
    app.state.mongo_client = client
    app.state.wishlist_repository = repository
    app.state.catalog_client = catalog_client
    try:
        yield
    finally:
        await catalog_client.close()
        await client.close()


app = FastAPI(title="Wishlist Service", version="1.0.0", lifespan=lifespan)


@app.get("/health")
async def health() -> dict[str, str]:
    client: AsyncMongoClient | None = getattr(app.state, "mongo_client", None)
    try:
        if client is None:
            raise PyMongoError("MongoDB client is not initialized")
        await client.admin.command("ping")
    except PyMongoError as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="MongoDB is unavailable",
        ) from exc
    return {"status": "UP"}


@app.get("/api/wishlist", response_model=WishlistResponse)
async def get_wishlist(
    user_id: Annotated[str, Depends(authenticated_user_id)],
    page: int = Query(default=0, ge=0),
    size: int = Query(default=20, ge=1, le=settings.max_page_size),
) -> WishlistResponse:
    repository: WishlistRepository = app.state.wishlist_repository
    items, total = await repository.list_items(user_id, page, size)
    return WishlistResponse(
        user_id=user_id,
        items=[WishlistItemResponse(product_id=item["productId"], added_at=item["addedAt"]) for item in items],
        page=page,
        size=size,
        total_elements=total,
        total_pages=repository.total_pages(total, size),
    )


@app.put("/api/wishlist/items/{product_id}", response_model=WishlistItemResponse)
async def add_to_wishlist(
    product_id: str,
    user_id: Annotated[str, Depends(authenticated_user_id)],
) -> WishlistItemResponse:
    if not product_id.strip() or len(product_id) > 200:
        raise HTTPException(status_code=422, detail="product_id must contain 1 to 200 characters")
    product_id = product_id.strip()
    catalog_client: CatalogClient = app.state.catalog_client
    await catalog_client.ensure_product_exists(product_id)
    repository: WishlistRepository = app.state.wishlist_repository
    item = await repository.add_item(user_id, product_id)
    return WishlistItemResponse(product_id=item["productId"], added_at=item["addedAt"])


@app.delete("/api/wishlist/items/{product_id}", status_code=status.HTTP_204_NO_CONTENT)
async def remove_from_wishlist(
    product_id: str,
    user_id: Annotated[str, Depends(authenticated_user_id)],
) -> Response:
    repository: WishlistRepository = app.state.wishlist_repository
    await repository.remove_item(user_id, product_id)
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@app.delete("/api/wishlist", status_code=status.HTTP_204_NO_CONTENT)
async def clear_wishlist(
    user_id: Annotated[str, Depends(authenticated_user_id)],
) -> Response:
    repository: WishlistRepository = app.state.wishlist_repository
    await repository.clear(user_id)
    return Response(status_code=status.HTTP_204_NO_CONTENT)
