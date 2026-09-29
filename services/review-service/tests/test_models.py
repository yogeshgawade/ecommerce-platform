import pytest
from pydantic import ValidationError

from app.models import CreateReviewRequest, UpdateReviewRequest


def test_create_review_trims_required_text():
    request = CreateReviewRequest(
        order_id=" order-1 ", rating=5, title=" Great ", body=" Good fit "
    )

    assert request.order_id == "order-1"
    assert request.title == "Great"
    assert request.body == "Good fit"


@pytest.mark.parametrize("field,value", [("order_id", "  "), ("title", " "), ("body", "\n")])
def test_create_review_rejects_blank_required_text(field, value):
    payload = {"order_id": "order-1", "rating": 4, "title": "Nice", "body": "Works"}
    payload[field] = value

    with pytest.raises(ValidationError):
        CreateReviewRequest(**payload)


@pytest.mark.parametrize("rating", [0, 6, -1])
def test_review_requests_reject_rating_outside_one_to_five(rating):
    with pytest.raises(ValidationError):
        UpdateReviewRequest(rating=rating, title="Nice", body="Works")


def test_update_review_rejects_blank_text():
    with pytest.raises(ValidationError):
        UpdateReviewRequest(rating=4, title=" Nice ", body="   ")
