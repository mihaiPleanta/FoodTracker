from __future__ import annotations

from datetime import date, timedelta

from sqlalchemy import desc, func, select
from sqlalchemy.ext.asyncio import AsyncSession

from models import FoodLog
from schemas import TopFood


async def get_top_foods(
    db: AsyncSession, uid: str, *, days: int = 60, limit: int = 10
) -> list[TopFood]:
    """Most-consumed foods for a user: grouped by name, ordered by number of
    logs desc, then total grams desc. Macros come from the most recent log of
    each name."""
    cutoff = date.today() - timedelta(days=days)

    agg_stmt = (
        select(
            FoodLog.name,
            func.count(FoodLog.id).label("freq"),
            func.sum(FoodLog.grams).label("total_grams"),
        )
        .where(FoodLog.uid == uid, FoodLog.log_date >= cutoff)
        .group_by(FoodLog.name)
        .order_by(desc("freq"), desc("total_grams"))
        .limit(limit)
    )
    rows = (await db.execute(agg_stmt)).all()

    result: list[TopFood] = []
    for name, _freq, _total in rows:
        rep_stmt = (
            select(FoodLog)
            .where(FoodLog.uid == uid, FoodLog.name == name)
            .order_by(desc(FoodLog.created_at), desc(FoodLog.id))
            .limit(1)
        )
        rep = (await db.execute(rep_stmt)).scalar_one()
        result.append(
            TopFood(
                name=rep.name,
                brand=rep.brand,
                kcal_100g=rep.kcal_100g,
                protein_100g=rep.protein_100g,
                carbs_100g=rep.carbs_100g,
                fat_100g=rep.fat_100g,
            )
        )
    return result
