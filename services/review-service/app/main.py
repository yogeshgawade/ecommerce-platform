import logging
from contextlib import asynccontextmanager

import httpx
from fastapi import Depends, FastAPI, HTTPException, Query, Response, status
from pymongo import AsyncMongoClient
from pymongo.errors import DuplicateKeyError, PyMongoError
from starlette.concurrency import run_in_threadpool

from .auth import CustomerIdentity, authenticated_customer
from .catalog_client import CatalogClient
from .config import settings
from .kafka_publisher import RatingPublisher
from .models import CreateReviewRequest, ReviewPageResponse, ReviewResponse, UpdateReviewRequest
from .order_client import OrderClient
from .repository import ReviewRepository

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    mongo_client = AsyncMongoClient(settings.mongodb_uri, serverSelectionTimeoutMS=5000, tz_aware=True)
    database = mongo_client.get_default_database()
    if database is None:
        raise RuntimeError("MONGODB_URI must include a database name")
    await mongo_client.admin.command("ping")
    repository = ReviewRepository(database.get_collection("reviews"))
    await repository.ensure_indexes()

    http_client = httpx.AsyncClient(timeout=httpx.Timeout(3.0, connect=1.0))
    app.state.mongo_client = mongo_client
    app.state.review_repository = repository
    app.state.catalog_client = CatalogClient(http_client)
    app.state.order_client = OrderClient(http_client)
    app.state.rating_publisher = RatingPublisher()
    try:
        yield
    finally:
        app.state.rating_publisher.close()
        await http_client.aclose()
        await mongo_client.close()


app = FastAPI(title="Review Service", version="1.0.0", lifespan=lifespan)


@app.get("/health")
async def health() -> dict[str, str]:
    mongo_client: AsyncMongoClient | None = getattr(app.state, "mongo_client", None)
    try:
        if mongo_client is None:
            raise PyMongoError("MongoDB client is not initialized")
        await mongo_client.admin.command("ping")
    except PyMongoError as exc:
        raise HTTPException(status_code=503, detail="MongoDB is unavailable") from exc
    return {"status": "UP"}


@app.get("/api/products/{product_id}/reviews", response_model=ReviewPageResponse)
async def list_product_reviews(
    product_id: str,
    page: int = Query(default=0, ge=0),
    size: int = Query(default=20, ge=1, le=settings.max_page_size),
) -> ReviewPageResponse:
    repository: ReviewRepository = app.state.review_repository
    reviews, total, average = await repository.get_page(product_id, page, size)
    return ReviewPageResponse(
        content=[ReviewRepository.to_response(review) for review in reviews],
        page=page,
        size=size,
        total_elements=total,
        total_pages=repository.total_pages(total, size),
        average_rating=average,
        review_count=total,
    )


@app.post("/api/products/{product_id}/reviews", response_model=ReviewResponse,
          status_code=status.HTTP_201_CREATED)
async def create_product_review(
    product_id: str,
    request: CreateReviewRequest,
    user: CustomerIdentity = Depends(authenticated_customer),
) -> ReviewResponse:
    if not product_id.strip() or len(product_id) > 200:
        raise HTTPException(status_code=422, detail="product_id must contain 1 to 200 characters")
    product_id = product_id.strip()
    await app.state.catalog_client.ensure_product_exists(product_id)
    await app.state.order_client.verify_confirmed_purchase(
        order_id=request.order_id,
        user_id=user.user_id,
        product_id=product_id,
        token=user.token,
    )
    repository: ReviewRepository = app.state.review_repository
    try:
        review = await repository.create(
            user_id=user.user_id,
            product_id=product_id,
            order_id=request.order_id,
            rating=request.rating,
            title=request.title,
            body=request.body,
        )
    except DuplicateKeyError as exc:
        raise HTTPException(status_code=409, detail="You have already reviewed this product") from exc
    average, count = await repository.get_summary(product_id)
    await _publish_rating(product_id, average, count)
    return ReviewResponse(**ReviewRepository.to_response(review))


@app.put("/api/products/{product_id}/reviews/me", response_model=ReviewResponse)
async def update_my_review(
    product_id: str,
    request: UpdateReviewRequest,
    user: CustomerIdentity = Depends(authenticated_customer),
) -> ReviewResponse:
    repository: ReviewRepository = app.state.review_repository
    review = await repository.update(
        user_id=user.user_id,
        product_id=product_id,
        rating=request.rating,
        title=request.title,
        body=request.body,
    )
    if review is None:
        raise HTTPException(status_code=404, detail="Review not found")
    average, count = await repository.get_summary(product_id)
    await _publish_rating(product_id, average, count)
    return ReviewResponse(**ReviewRepository.to_response(review))


@app.delete("/api/products/{product_id}/reviews/me", status_code=status.HTTP_204_NO_CONTENT)
async def delete_my_review(
    product_id: str,
    user: CustomerIdentity = Depends(authenticated_customer),
) -> Response:
    repository: ReviewRepository = app.state.review_repository
    deleted = await repository.delete(user_id=user.user_id, product_id=product_id)
    if not deleted:
        raise HTTPException(status_code=404, detail="Review not found")
    average, count = await repository.get_summary(product_id)
    await _publish_rating(product_id, average, count)
    return Response(status_code=status.HTTP_204_NO_CONTENT)


async def _publish_rating(product_id: str, average: float | None, count: int) -> None:
    publisher: RatingPublisher = app.state.rating_publisher
    try:
        await run_in_threadpool(publisher.publish, product_id, average, count)
    except Exception:
        # The review is already stored. Logging allows a retry by editing the review,
        # and keeps a temporary Kafka failure from turning a successful write into an error.
        logger.exception("Could not publish rating update for product %s", product_id)
