from datetime import date, datetime, timedelta, timezone
from typing import Dict, List, Optional

from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from auth import verify_token
from database import get_db
from models import DayStatus, FoodLog, HydrationLog, Profile, WeightCheckIn
from schemas import (
    MEAL_KEYS,
    DayResponse,
    DayStatusResponse,
    DayStatusUpdate,
    FoodLogDto,
    WeightCheckInDto,
)
from services.nutrition import calculate_goals

router = APIRouter()

def day_food_logs_query(uid: str, day: date):
    return (
        select(FoodLog)
        .where(FoodLog.uid == uid, FoodLog.log_date == day)
        .order_by(FoodLog.created_at.asc(), FoodLog.id.asc())
    )


def status_label(row: Optional[DayStatus]) -> Optional[str]:
    if row is None:
        return None
    return "complete" if row.complete else "incomplete"


@router.get("/days/{day}", response_model=DayResponse, response_model_by_alias=False)
async def get_day(
    day: date,
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> DayResponse:
    uid = token["uid"]

    food_rows = (
        await db.execute(
            day_food_logs_query(uid, day)
        )
    ).scalars().all()

    foods_by_meal: Dict[str, List[FoodLogDto]] = {key: [] for key in MEAL_KEYS.values()}
    for row in food_rows:
        meal_key = MEAL_KEYS.get(row.meal)
        if meal_key is None:
            continue
        foods_by_meal[meal_key].append(FoodLogDto.model_validate(row))

    hydration_row = await db.scalar(
        select(HydrationLog).where(
            HydrationLog.uid == uid,
            HydrationLog.log_date == day,
        )
    )
    hydration_liters = hydration_row.liters if hydration_row is not None else 0.0

    weight_row = await db.scalar(
        select(WeightCheckIn).where(
            WeightCheckIn.uid == uid,
            WeightCheckIn.log_date == day,
        )
    )
    weight_dto = (
        WeightCheckInDto.model_validate(weight_row) if weight_row is not None else None
    )

    status_row = await db.get(DayStatus, (uid, day))

    return DayResponse(
        foods_by_meal=foods_by_meal,
        hydration_liters=hydration_liters,
        weight_check_in=weight_dto,
        status=status_label(status_row),
    )


@router.put("/days/{day}/status", response_model=DayStatusResponse)
async def put_day_status(
    day: date,
    body: DayStatusUpdate,
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> DayStatusResponse:
    uid = token["uid"]

    # The client sends its local date; tolerate UTC being a day behind it.
    if day > datetime.now(timezone.utc).date() + timedelta(days=1):
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="Date is too far in the future",
        )

    profile = await db.scalar(select(Profile).where(Profile.uid == uid))
    if profile is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Profile not found")

    row = await db.get(DayStatus, (uid, day))
    if row is None:
        row = DayStatus(uid=uid, log_date=day)
        db.add(row)
    row.complete = body.complete
    if body.complete:
        goals = calculate_goals(profile)
        row.calorie_goal = goals.calorie_goal
        row.protein_goal_g = goals.protein_goal_g
        row.carbs_goal_g = goals.carbs_goal_g
        row.fat_goal_g = goals.fat_goal_g
        row.tdee = goals.tdee
    else:
        row.calorie_goal = None
        row.protein_goal_g = None
        row.carbs_goal_g = None
        row.fat_goal_g = None
        row.tdee = None
    await db.commit()

    return DayStatusResponse(date=day, status=status_label(row))


@router.delete("/days/{day}/status", status_code=status.HTTP_204_NO_CONTENT)
async def delete_day_status(
    day: date,
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> Response:
    await db.execute(
        delete(DayStatus).where(DayStatus.uid == token["uid"], DayStatus.log_date == day)
    )
    await db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
