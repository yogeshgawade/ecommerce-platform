from dataclasses import dataclass

import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from jwt import InvalidTokenError

from .config import settings

if len(settings.app_jwt_secret.encode("utf-8")) < 32:
    raise RuntimeError("APP_JWT_SECRET must contain at least 32 bytes")

bearer_scheme = HTTPBearer(auto_error=False)


@dataclass(frozen=True)
class CustomerIdentity:
    user_id: str
    token: str


async def authenticated_customer(
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer_scheme),
) -> CustomerIdentity:
    if credentials is None or credentials.scheme.lower() != "bearer":
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Bearer token required",
            headers={"WWW-Authenticate": "Bearer"},
        )

    try:
        claims = jwt.decode(credentials.credentials, settings.app_jwt_secret, algorithms=["HS256"])
    except InvalidTokenError as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid bearer token",
            headers={"WWW-Authenticate": "Bearer"},
        ) from exc

    subject = claims.get("sub")
    roles = claims.get("roles")
    if not isinstance(subject, str) or not subject.strip() or not isinstance(roles, list):
        raise HTTPException(status_code=401, detail="Bearer token has invalid claims")
    if "CUSTOMER" not in roles:
        raise HTTPException(status_code=403, detail="Only customers can manage reviews")
    return CustomerIdentity(user_id=subject, token=credentials.credentials)
