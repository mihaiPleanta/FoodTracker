from collections import defaultdict
from datetime import date, timedelta
from typing import Dict, List

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from auth import verify_token
from database import get_db
from models import DayStatus, FoodLog, HydrationLog, WeightCheckIn
from routers.days import status_label
from schemas import (
    StatsDayDto,
    StatsDaysResponse,
    StatsGoalsDto,
    StatsWeightDto,
    TopFoodStatDto,
    TopFoodsDto,
)

router = APIRouter()

_MAX_STATS_DAYS = 62
_WEIGHT_WARMUP_DAYS = 60
_TOP_FOODS_LIMIT = 5


def _top_foods(logs, complete_days) -> TopFoodsDto:
    groups: Dict[str, dict] = {}
    for row in logs:
        if row.log_date not in complete_days:
            continue
        key = "recipe:" + row.name if row.barcode == "recipe" else row.barcode
        g = groups.setdefault(
            key, {"kcal": 0.0, "protein": 0.0, "dates": set(), "latest": None, "row": None}
        )
        f = row.grams / 100
        g["kcal"] += row.kcal_100g * f
        g["protein"] += row.protein_100g * f
        g["dates"].add(row.log_date)
        order = (row.log_date, row.created_at)
        if g["latest"] is None or order >= g["latest"]:
            g["latest"] = order
            g["row"] = row
    stats = [
        TopFoodStatDto(
            key=key,
            name=g["row"].name,
            brand=g["row"].brand,
            total_kcal=round(g["kcal"]),
            total_protein_g=round(g["protein"], 1),
            days_count=len(g["dates"]),
        )
        for key, g in groups.items()
    ]
    by_kcal = sorted(stats, key=lambda s: (-s.total_kcal, s.name))[:_TOP_FOODS_LIMIT]
    by_protein = sorted(stats, key=lambda s: (-s.total_protein_g, s.name))[:_TOP_FOODS_LIMIT]
    return TopFoodsDto(by_kcal=by_kcal, by_protein=by_protein)


@router.get("/stats/days", response_model=StatsDaysResponse)
async def stats_days(
    to: date,
    from_: date = Query(alias="from"),
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> StatsDaysResponse:
    if from_ > to:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="from must be <= to"
        )
    if (to - from_).days + 1 > _MAX_STATS_DAYS:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=f"Range cannot exceed {_MAX_STATS_DAYS} days",
        )
    uid = token["uid"]

    logs = (
        await db.execute(
            select(FoodLog).where(
                FoodLog.uid == uid, FoodLog.log_date >= from_, FoodLog.log_date <= to
            )
        )
    ).scalars().all()
    statuses = (
        await db.execute(
            select(DayStatus).where(
                DayStatus.uid == uid, DayStatus.log_date >= from_, DayStatus.log_date <= to
            )
        )
    ).scalars().all()
    hydration = (
        await db.execute(
            select(HydrationLog).where(
                HydrationLog.uid == uid,
                HydrationLog.log_date >= from_,
                HydrationLog.log_date <= to,
            )
        )
    ).scalars().all()
    weights = (
        await db.execute(
            select(WeightCheckIn)
            .where(
                WeightCheckIn.uid == uid,
                WeightCheckIn.log_date >= from_ - timedelta(days=_WEIGHT_WARMUP_DAYS),
                WeightCheckIn.log_date <= to,
            )
            .order_by(WeightCheckIn.log_date.asc())
        )
    ).scalars().all()

    # [kcal, protein, carbs, fat, count]
    totals: Dict[date, List[float]] = defaultdict(lambda: [0.0, 0.0, 0.0, 0.0, 0])
    for row in logs:
        t = totals[row.log_date]
        f = row.grams / 100
        t[0] += row.kcal_100g * f
        t[1] += row.protein_100g * f
        t[2] += row.carbs_100g * f
        t[3] += row.fat_100g * f
        t[4] += 1
    status_by_day = {r.log_date: r for r in statuses}
    water_by_day = {r.log_date: r.liters for r in hydration if r.liters > 0}

    day_keys = sorted(set(totals) | set(status_by_day) | set(water_by_day))
    days: List[StatsDayDto] = []
    for d in day_keys:
        t = totals.get(d, [0.0, 0.0, 0.0, 0.0, 0])
        srow = status_by_day.get(d)
        label = status_label(srow)
        goals = None
        if (
            label == "complete"
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
        days.append(
            StatsDayDto(
                date=d,
                kcal=round(t[0]),
                protein_g=round(t[1], 1),
                carbs_g=round(t[2], 1),
                fat_g=round(t[3], 1),
                has_food=t[4] > 0,
                status=label,
                goals=goals,
                hydration_l=water_by_day.get(d, 0.0),
            )
        )

    return StatsDaysResponse(
        days=days,
        weights=[StatsWeightDto(date=w.log_date, weight_kg=w.weight_kg) for w in weights],
        top_foods=_top_foods(
            logs, {d for d, r in status_by_day.items() if status_label(r) == "complete"}
        ),
    )
