import jwt
import pytest
from fastapi import HTTPException
from fastapi.security import HTTPAuthorizationCredentials

from app.auth import authenticated_customer
from app.config import settings


def bearer(token: str) -> HTTPAuthorizationCredentials:
    return HTTPAuthorizationCredentials(scheme="Bearer", credentials=token)


@pytest.mark.asyncio
async def test_authenticated_customer_returns_subject_and_token():
    token = jwt.encode(
        {"sub": "customer-123", "roles": ["CUSTOMER"]},
        settings.app_jwt_secret,
        algorithm="HS256",
    )

    identity = await authenticated_customer(bearer(token))

    assert identity.user_id == "customer-123"
    assert identity.token == token


@pytest.mark.asyncio
async def test_authenticated_customer_rejects_missing_credentials():
    with pytest.raises(HTTPException) as error:
        await authenticated_customer(None)

    assert error.value.status_code == 401
    assert error.value.headers["WWW-Authenticate"] == "Bearer"


@pytest.mark.asyncio
async def test_authenticated_customer_rejects_bad_signature():
    token = jwt.encode(
        {"sub": "customer-123", "roles": ["CUSTOMER"]},
        "a-different-secret-with-sufficient-length-for-hs256",
        algorithm="HS256",
    )

    with pytest.raises(HTTPException) as error:
        await authenticated_customer(bearer(token))

    assert error.value.status_code == 401


@pytest.mark.asyncio
async def test_authenticated_customer_rejects_non_customer_role():
    token = jwt.encode(
        {"sub": "admin-123", "roles": ["ADMIN"]},
        settings.app_jwt_secret,
        algorithm="HS256",
    )

    with pytest.raises(HTTPException) as error:
        await authenticated_customer(bearer(token))

    assert error.value.status_code == 403


@pytest.mark.asyncio
async def test_authenticated_customer_rejects_missing_roles():
    token = jwt.encode({"sub": "customer-123"}, settings.app_jwt_secret, algorithm="HS256")

    with pytest.raises(HTTPException) as error:
        await authenticated_customer(bearer(token))

    assert error.value.status_code == 401
