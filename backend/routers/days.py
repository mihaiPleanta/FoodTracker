from datetime import date, datetime, timedelta, timezone
from typing import Dict, List, Optional

from fastapi import APIRouter, Depends, HTTPException, Query, Response, status
from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from auth import verify_token
from database import get_db
from models import DayStatus, FoodLog, HydrationLog, Profile, WeightCheckIn
from schemas import (
    MEAL_KEYS,
    DayRangeItem,
    DayResponse,
    DaysRangeResponse,
    DayStatusResponse,
    DayStatusUpdate,
    FoodLogDto,
    StatsGoalsDto,
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


_MAX_RANGE_DAYS = 62


def _build_day(
    food_rows: List[FoodLog],
    hydration_row: Optional[HydrationLog],
    weight_row: Optional[WeightCheckIn],
    status_row: Optional[DayStatus],
) -> DayResponse:
    foods_by_meal: Dict[str, List[FoodLogDto]] = {key: [] for key in MEAL_KEYS.values()}
    for row in food_rows:
        meal_key = MEAL_KEYS.get(row.meal)
        if meal_key is None:
            continue
        foods_by_meal[meal_key].append(FoodLogDto.model_validate(row))
    return DayResponse(
        foods_by_meal=foods_by_meal,
        hydration_liters=hydration_row.liters if hydration_row is not None else 0.0,
        weight_check_in=(
            WeightCheckInDto.model_validate(weight_row) if weight_row is not None else None
        ),
        status=status_label(status_row),
    )


@router.get("/days", response_model=DaysRangeResponse, response_model_by_alias=False)
async def get_days_range(
    to: date,
    from_: date = Query(alias="from"),
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> DaysRangeResponse:
    uid = token["uid"]
    if from_ > to:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="from must be <= to"
        )
    if (to - from_).days + 1 > _MAX_RANGE_DAYS:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=f"Range is limited to {_MAX_RANGE_DAYS} days",
        )

    food_rows = (
        await db.execute(
            select(FoodLog)
            .where(FoodLog.uid == uid, FoodLog.log_date >= from_, FoodLog.log_date <= to)
            .order_by(FoodLog.created_at.asc(), FoodLog.id.asc())
        )
    ).scalars().all()
    hydration_rows = (
        await db.execute(
            select(HydrationLog).where(
                HydrationLog.uid == uid,
                HydrationLog.log_date >= from_,
                HydrationLog.log_date <= to,
            )
        )
    ).scalars().all()
    weight_rows = (
        await db.execute(
            select(WeightCheckIn).where(
                WeightCheckIn.uid == uid,
                WeightCheckIn.log_date >= from_,
                WeightCheckIn.log_date <= to,
            )
        )
    ).scalars().all()
    status_rows = (
        await db.execute(
            select(DayStatus).where(
                DayStatus.uid == uid, DayStatus.log_date >= from_, DayStatus.log_date <= to
            )
        )
    ).scalars().all()

    foods: Dict[date, List[FoodLog]] = {}
    for row in food_rows:
        foods.setdefault(row.log_date, []).append(row)
    hydration = {r.log_date: r for r in hydration_rows if r.liters > 0}
    weights = {r.log_date: r for r in weight_rows}
    statuses = {r.log_date: r for r in status_rows}

    items: List[DayRangeItem] = []
    for d in sorted(set(foods) | set(hydration) | set(weights) | set(statuses)):
        srow = statuses.get(d)
        day = _build_day(foods.get(d, []), hydration.get(d), weights.get(d), srow)
        goals = None
        if (
            srow is not None
            and srow.complete
            and srow.calorie_goal is not None
            and srow.protein_goal_g is not None
            and srow.carbs_goal_g is not None
            and srow.fat_goal_g is not None
            and srow.tdee is not None
        ):
            goals = StatsGoalsDto(
                calorie_goal=srow.calorie_goal,
                protein_goal_g=srow.protein_goal_g,
                carbs_goal_g=srow.carbs_goal_g,
                fat_goal_g=srow.fat_goal_g,
                tdee=srow.tdee,
            )
        items.append(DayRangeItem(**day.model_dump(), date=d, goals=goals))
    return DaysRangeResponse(days=items)


@router.get("/days/{day}", response_model=DayResponse, response_model_by_alias=False)
async def get_day(
    day: date,
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> DayResponse:
    uid = token["uid"]
    food_rows = (await db.execute(day_food_logs_query(uid, day))).scalars().all()
    hydration_row = await db.scalar(
        select(HydrationLog).where(HydrationLog.uid == uid, HydrationLog.log_date == day)
    )
    weight_row = await db.scalar(
        select(WeightCheckIn).where(WeightCheckIn.uid == uid, WeightCheckIn.log_date == day)
    )
    status_row = await db.get(DayStatus, (uid, day))
    return _build_day(food_rows, hydration_row, weight_row, status_row)


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
