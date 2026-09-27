from fastapi.testclient import TestClient

from app.main import app
from app.redis_client import cart_key, redis_client


def setup_function():
    redis_client.delete(cart_key("user-1"))


def teardown_function():
    redis_client.delete(cart_key("user-1"))


def test_health():
    with TestClient(app) as client:
        response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "UP"}


def test_get_empty_cart():
    with TestClient(app) as client:
        response = client.get("/api/carts/user-1")

    assert response.status_code == 200
    assert response.json() == {
        "user_id": "user-1",
        "items": [],
    }


def test_add_item():
    payload = {
        "product_id": "product-1",
        "name": "Running Shoes",
        "price": "2999.00",
        "quantity": 2,
    }

    with TestClient(app) as client:
        response = client.post(
            "/api/carts/user-1/items",
            json=payload,
        )

    assert response.status_code == 200
    assert response.json()["items"][0]["product_id"] == "product-1"
    assert response.json()["items"][0]["quantity"] == 2


def test_add_same_item_increases_quantity():
    payload = {
        "product_id": "product-1",
        "name": "Running Shoes",
        "price": "2999.00",
        "quantity": 2,
    }

    with TestClient(app) as client:
        client.post("/api/carts/user-1/items", json=payload)
        response = client.post("/api/carts/user-1/items", json=payload)

    assert response.status_code == 200
    assert response.json()["items"][0]["quantity"] == 4


def test_update_item():
    with TestClient(app) as client:
        client.post(
            "/api/carts/user-1/items",
            json={
                "product_id": "product-1",
                "name": "Running Shoes",
                "price": "2999.00",
                "quantity": 2,
            },
        )

        response = client.patch(
            "/api/carts/user-1/items/product-1",
            json={"quantity": 5},
        )

    assert response.status_code == 200
    assert response.json()["items"][0]["quantity"] == 5


def test_remove_item():
    with TestClient(app) as client:
        client.post(
            "/api/carts/user-1/items",
            json={
                "product_id": "product-1",
                "name": "Running Shoes",
                "price": "2999.00",
                "quantity": 2,
            },
        )

        response = client.delete(
            "/api/carts/user-1/items/product-1",
        )

    assert response.status_code == 200
    assert response.json()["items"] == []


def test_clear_cart():
    with TestClient(app) as client:
        client.post(
            "/api/carts/user-1/items",
            json={
                "product_id": "product-1",
                "name": "Running Shoes",
                "price": "2999.00",
                "quantity": 1,
            },
        )

        response = client.delete("/api/carts/user-1")
        cart_response = client.get("/api/carts/user-1")

    assert response.status_code == 204
    assert cart_response.status_code == 200
    assert cart_response.json()["items"] == []
