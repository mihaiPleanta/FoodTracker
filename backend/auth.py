from typing import Callable

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


def get_user_deleter() -> Callable[[str], None]:
    """FastAPI dependency — returnează un callable care șterge userul Firebase.

    Injectabil ca să poată fi suprascris în teste (app.dependency_overrides),
    la fel ca clienții OFF / generic / Ollama. UserNotFound e ignorat (idempotent);
    orice altă eroare propagă către endpoint → 502.
    """
    def _delete(uid: str) -> None:
        try:
            firebase_admin.auth.delete_user(uid)
        except firebase_admin.auth.UserNotFoundError:
            pass
    return _delete
