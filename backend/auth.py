import firebase_admin.auth
from fastapi import Header, HTTPException, status


async def verify_token(authorization: str = Header(...)) -> dict:
    """FastAPI dependency — verifică Bearer token Firebase și returnează payload."""
    if not authorization.startswith("Bearer "):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authorization header must be 'Bearer <token>'"
        )
    token = authorization[len("Bearer "):]
    try:
        return firebase_admin.auth.verify_id_token(token)
    except firebase_admin.auth.InvalidIdTokenError:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token")
    except firebase_admin.auth.ExpiredIdTokenError:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Token expired")
