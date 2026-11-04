from datetime import date, datetime, timedelta, timezone
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from auth import verify_token
from database import get_db
from models import FoodLog, Profile, StreakRecovery
from schemas import (
    RecoveryOfferDto,
    StreakDayDto,
    StreakResponse,
    StreakTodayRequest,
)
from services.streak import RecoveryOffer, compute_streak, next_milestone

router = APIRouter()


def _check_today(today: date) -> None:
    # The client sends its local date; tolerate UTC being a day behind it.
    if today > datetime.now(timezone.utc).date() + timedelta(days=1):
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="Date is too far in the future",
        )


async def _compute(uid: str, today: date, db: AsyncSession):
    logged = set(
        (await db.execute(select(FoodLog.log_date).where(FoodLog.uid == uid).distinct()))
        .scalars()
        .all()
    )
    rows = (
        await db.execute(select(StreakRecovery).where(StreakRecovery.uid == uid))
    ).scalars().all()
    recovered = [r for r in rows if r.kind == "recovered"]
    ranges = [(r.gap_start, r.gap_end) for r in recovered]
    declined = {r.gap_start for r in rows if r.kind == "declined"}
    used_on: Optional[date] = next(
        (
            r.used_on
            for r in recovered
            if r.used_on.year == today.year and r.used_on.month == today.month
        ),
        None,
    )
    result = compute_streak(logged, ranges, declined, used_on is not None, today)
    return result, used_on


def _response(result, used_on: Optional[date]) -> StreakResponse:
    offer = result.offer
    return StreakResponse(
        current=result.current,
        best=result.best,
        total_logged_days=result.total_logged_days,
        today_logged=result.today_logged,
        pause_used_this_week=result.pause_used_on is not None,
        pause_used_on=result.pause_used_on,
        recovery_available_this_month=used_on is None,
        recovery_used_on=used_on,
        last_14_days=[StreakDayDto(date=d, state=s) for d, s in result.last_14_days],
        next_milestone=next_milestone(result.current),
        recovery_offer=(
            RecoveryOfferDto(
                previous_streak=offer.previous_streak,
                gap_start=offer.gap_start,
                gap_end=offer.gap_end,
            )
            if offer is not None
            else None
        ),
    )


@router.get("/streak", response_model=StreakResponse)
async def get_streak(
    today: date,
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> StreakResponse:
    _check_today(today)
    result, used_on = await _compute(token["uid"], today, db)
    return _response(result, used_on)


async def _record(kind: str, body: StreakTodayRequest, uid: str, db: AsyncSession):
    _check_today(body.today)
    profile = await db.scalar(select(Profile).where(Profile.uid == uid))
    if profile is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Profile not found")
    result, _ = await _compute(uid, body.today, db)
    offer: Optional[RecoveryOffer] = result.offer
    if offer is None:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT, detail="No recovery available"
        )
    db.add(
        StreakRecovery(
            uid=uid,
            gap_start=offer.gap_start,
            gap_end=offer.gap_end,
            kind=kind,
            used_on=body.today,
        )
    )
    await db.commit()
    result, used_on = await _compute(uid, body.today, db)
    return _response(result, used_on)


@router.post("/streak/recover", response_model=StreakResponse)
async def recover_streak(
    body: StreakTodayRequest,
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> StreakResponse:
    return await _record("recovered", body, token["uid"], db)


@router.post("/streak/decline", response_model=StreakResponse)
async def decline_streak(
    body: StreakTodayRequest,
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> StreakResponse:
    return await _record("declined", body, token["uid"], db)
