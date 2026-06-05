from typing import Callable

from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from auth import get_user_deleter, verify_token
from database import get_db
from models import Profile
from schemas import ProfileRequest, ProfileResponse

router = APIRouter()


@router.get("/profile", response_model=ProfileResponse)
async def get_profile(
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> Profile:
    uid = token["uid"]
    result = await db.execute(select(Profile).where(Profile.uid == uid))
    profile = result.scalar_one_or_none()
    if profile is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Profile not found")
    return profile


@router.post("/profile", response_model=ProfileResponse)
async def save_profile(
    req: ProfileRequest,
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> Profile:
    uid = token["uid"]
    result = await db.execute(select(Profile).where(Profile.uid == uid))
    profile = result.scalar_one_or_none()
    if profile is None:
        profile = Profile(uid=uid, **req.model_dump())
        db.add(profile)
    else:
        for key, value in req.model_dump().items():
            setattr(profile, key, value)
    await db.commit()
    await db.refresh(profile)
    return profile


@router.delete("/profile", status_code=status.HTTP_204_NO_CONTENT)
async def delete_profile(
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
    delete_user: Callable[[str], None] = Depends(get_user_deleter),
) -> Response:
    uid = token["uid"]
    # Firebase first: if it fails, nothing is committed to the DB → consistent state.
    try:
        delete_user(uid)
    except Exception:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="Failed to delete auth user",
        )
    result = await db.execute(select(Profile).where(Profile.uid == uid))
    profile = result.scalar_one_or_none()
    if profile is not None:
        await db.delete(profile)
        await db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
