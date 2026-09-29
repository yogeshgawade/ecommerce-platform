import jwt
import pytest
from fastapi import HTTPException
from fastapi.security import HTTPAuthorizationCredentials

from app.auth import authenticated_user_id
from app.config import settings


@pytest.mark.asyncio
async def test_authenticated_user_id_returns_verified_subject():
    token = jwt.encode({"sub": "customer-123"}, settings.app_jwt_secret, algorithm="HS256")

    user_id = await authenticated_user_id(
        HTTPAuthorizationCredentials(scheme="Bearer", credentials=token)
    )

    assert user_id == "customer-123"


@pytest.mark.asyncio
async def test_authenticated_user_id_rejects_missing_credentials():
    with pytest.raises(HTTPException) as error:
        await authenticated_user_id(None)

    assert error.value.status_code == 401
    assert error.value.headers["WWW-Authenticate"] == "Bearer"


@pytest.mark.asyncio
async def test_authenticated_user_id_rejects_invalid_signature():
    token = jwt.encode({"sub": "customer-123"}, "wrong-secret-that-is-long-enough-for-hs256", algorithm="HS256")

    with pytest.raises(HTTPException) as error:
        await authenticated_user_id(
            HTTPAuthorizationCredentials(scheme="Bearer", credentials=token)
        )

    assert error.value.status_code == 401


@pytest.mark.asyncio
async def test_authenticated_user_id_rejects_missing_subject():
    token = jwt.encode({"email": "customer@example.com"}, settings.app_jwt_secret, algorithm="HS256")

    with pytest.raises(HTTPException) as error:
        await authenticated_user_id(
            HTTPAuthorizationCredentials(scheme="Bearer", credentials=token)
        )

    assert error.value.status_code == 401
