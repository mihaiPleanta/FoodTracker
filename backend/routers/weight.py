from datetime import date

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from auth import verify_token
from database import get_db
from models import Profile, WeightCheckIn
from schemas import (
    WeightCheckInCreate,
    WeightCheckInDto,
    WeightCheckInListDto,
)

router = APIRouter()

_MAX_RANGE_DAYS = 366


@router.post(
    "/weight-check-ins",
    response_model=WeightCheckInDto,
    response_model_by_alias=False,
)
async def post_weight_check_in(
    req: WeightCheckInCreate,
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> WeightCheckIn:
    uid = token["uid"]
    result = await db.execute(
        select(WeightCheckIn).where(
            WeightCheckIn.uid == uid,
            WeightCheckIn.log_date == req.date,
        )
    )
    row = result.scalar_one_or_none()
    if row is None:
        row = WeightCheckIn(uid=uid, log_date=req.date, weight_kg=req.weight_kg)
        db.add(row)
    else:
        row.weight_kg = req.weight_kg

    # Side-effect: sync profiles.current_weight_kg if this is the latest check-in.
    # Flush so the current row is visible in MAX().
    await db.flush()
    max_date = await db.scalar(
        select(func.max(WeightCheckIn.log_date)).where(WeightCheckIn.uid == uid)
    )
    if max_date == req.date:
        profile = await db.scalar(select(Profile).where(Profile.uid == uid))
        if profile is not None:
            profile.current_weight_kg = req.weight_kg

    await db.commit()
    await db.refresh(row)
    return row


@router.get(
    "/weight-check-ins",
    response_model=WeightCheckInListDto,
    response_model_by_alias=False,
)
async def get_weight_check_ins(
    from_: date = Query(..., alias="from"),
    to: date = Query(...),
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> WeightCheckInListDto:
    if to < from_:
        raise HTTPException(status_code=422, detail="'to' must be >= 'from'")
    if (to - from_).days > _MAX_RANGE_DAYS:
        raise HTTPException(status_code=422, detail=f"Range exceeds {_MAX_RANGE_DAYS} days")
    uid = token["uid"]
    result = await db.execute(
        select(WeightCheckIn)
        .where(
            WeightCheckIn.uid == uid,
            WeightCheckIn.log_date >= from_,
            WeightCheckIn.log_date <= to,
        )
        .order_by(WeightCheckIn.log_date.asc())
    )
    rows = result.scalars().all()
    return WeightCheckInListDto(items=[WeightCheckInDto.model_validate(r) for r in rows])
