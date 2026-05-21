from datetime import date
from typing import Dict, List

from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from auth import verify_token
from database import get_db
from models import FoodLog, HydrationLog, WeightCheckIn
from schemas import DayResponse, FoodLogDto, WeightCheckInDto

router = APIRouter()

_MEAL_KEYS = {
    "BREAKFAST": "breakfast",
    "LUNCH": "lunch",
    "DINNER": "dinner",
    "SNACKS": "snacks",
}


@router.get("/days/{day}", response_model=DayResponse, response_model_by_alias=False)
async def get_day(
    day: date,
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> DayResponse:
    uid = token["uid"]

    food_rows = (
        await db.execute(
            select(FoodLog)
            .where(FoodLog.uid == uid, FoodLog.log_date == day)
            .order_by(FoodLog.created_at.asc())
        )
    ).scalars().all()

    foods_by_meal: Dict[str, List[FoodLogDto]] = {key: [] for key in _MEAL_KEYS.values()}
    for row in food_rows:
        meal_key = _MEAL_KEYS.get(row.meal)
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

    return DayResponse(
        foods_by_meal=foods_by_meal,
        hydration_liters=hydration_liters,
        weight_check_in=weight_dto,
    )
