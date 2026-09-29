import os

import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from jwt import InvalidTokenError

from dotenv import load_dotenv

load_dotenv()

JWT_SECRET = os.getenv(
    "APP_JWT_SECRET",
    "local-development-secret-change-this-to-a-long-random-value",
)
if len(JWT_SECRET.encode("utf-8")) < 32:
    raise RuntimeError("APP_JWT_SECRET must contain at least 32 bytes")

bearer_scheme = HTTPBearer(auto_error=False)


async def authenticated_user_id(
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer_scheme),
) -> str:
    if credentials is None or credentials.scheme.lower() != "bearer":
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Bearer token required",
            headers={"WWW-Authenticate": "Bearer"},
        )

    try:
        claims = jwt.decode(credentials.credentials, JWT_SECRET, algorithms=["HS256"])
    except InvalidTokenError as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid bearer token",
            headers={"WWW-Authenticate": "Bearer"},
        ) from exc

    subject = claims.get("sub")
    if not isinstance(subject, str) or not subject.strip():
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Bearer token has no user subject",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return subject


async def require_cart_owner(
    user_id: str,
    token_user_id: str = Depends(authenticated_user_id),
) -> str:
    if user_id != token_user_id:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Cannot access another user's cart")
    return token_user_id
