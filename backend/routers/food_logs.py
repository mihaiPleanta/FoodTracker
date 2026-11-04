import uuid
from datetime import date
from typing import Dict, List

from fastapi import APIRouter, Depends, HTTPException, Query, Response, status
from sqlalchemy import delete, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from auth import verify_token
from database import get_db
from models import FoodLog
from schemas import (
    MEAL_KEYS,
    DaySummary,
    FoodLogCopyRequest,
    FoodLogCreate,
    FoodLogDeleteBatchRequest,
    FoodLogDeleteBatchResponse,
    FoodLogDto,
    FoodLogSummaryResponse,
    FoodLogUpdate,
    MealSummary,
)

router = APIRouter()


def _totals_from_ingredients(ingredients):
    """Total grams + per-100g macros (so grams * per100g / 100 == total) for a
    recipe entry composed of ingredients carrying their own per-100g macros."""
    total_g = sum(i.grams for i in ingredients)
    kcal = sum(i.kcal_100g * i.grams / 100 for i in ingredients)
    p = sum(i.protein_100g * i.grams / 100 for i in ingredients)
    c = sum(i.carbs_100g * i.grams / 100 for i in ingredients)
    f = sum(i.fat_100g * i.grams / 100 for i in ingredients)
    per = (lambda x: x / total_g * 100) if total_g else (lambda x: 0.0)
    return round(total_g), per(kcal), per(p), per(c), per(f)


async def _find_by_client_id(db: AsyncSession, uid: str, client_id: str):
    return (
        await db.execute(
            select(FoodLog).where(FoodLog.uid == uid, FoodLog.client_id == client_id)
        )
    ).scalar_one_or_none()


def _apply_update(row: FoodLog, req: FoodLogUpdate) -> None:
    if req.ingredients:
        g, k, p, c, f = _totals_from_ingredients(req.ingredients)
        row.grams = g
        row.kcal_100g, row.protein_100g, row.carbs_100g, row.fat_100g = k, p, c, f
        row.ingredients = [i.model_dump() for i in req.ingredients]
    else:
        row.grams = req.grams
        row.unit = req.unit
        row.unit_qty = req.unit_qty


@router.post(
    "/food-logs",
    response_model=FoodLogDto,
    status_code=status.HTTP_201_CREATED,
)
async def create_food_log(
    req: FoodLogCreate,
    response: Response,
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> FoodLog:
    uid = token["uid"]
    data = req.model_dump()
    data["client_id"] = data["client_id"] or str(uuid.uuid4())
    existing = await _find_by_client_id(db, uid, data["client_id"])
    if existing is not None:
        response.status_code = status.HTTP_200_OK
        return existing
    row = FoodLog(uid=uid, **data)
    db.add(row)
    try:
        await db.commit()
    except IntegrityError:
        # Concurrent create with the same client_id: return the winner.
        await db.rollback()
        existing = await _find_by_client_id(db, uid, data["client_id"])
        if existing is None:
            raise
        response.status_code = status.HTTP_200_OK
        return existing
    await db.refresh(row)
    return row


@router.post(
    "/food-logs/copy",
    response_model=List[FoodLogDto],
    status_code=status.HTTP_201_CREATED,
)
async def copy_food_logs(
    req: FoodLogCopyRequest,
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> List[FoodLog]:
    uid = token["uid"]
    ids = {item.source_id for item in req.items}
    sources = {
        r.id: r
        for r in (
            await db.execute(select(FoodLog).where(FoodLog.id.in_(ids), FoodLog.uid == uid))
        ).scalars().all()
    }
    if len(sources) != len(ids):
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Food log not found")

    new_rows = []
    for item in req.items:
        src = sources[item.source_id]
        row = FoodLog(
            uid=uid,
            client_id=str(uuid.uuid4()),
            log_date=req.to_date,
            meal=src.meal,
            barcode=src.barcode,
            name=src.name,
            brand=src.brand,
            image_url=src.image_url,
            categories=src.categories,
        )
        if item.ingredients:
            g, k, p, c, f = _totals_from_ingredients(item.ingredients)
            row.grams = g
            row.kcal_100g, row.protein_100g, row.carbs_100g, row.fat_100g = k, p, c, f
            row.ingredients = [i.model_dump() for i in item.ingredients]
            row.unit = None
            row.unit_qty = None
        elif src.ingredients is not None:
            row.grams = src.grams
            row.kcal_100g, row.protein_100g, row.carbs_100g, row.fat_100g = (
                src.kcal_100g, src.protein_100g, src.carbs_100g, src.fat_100g,
            )
            row.ingredients = src.ingredients
            row.unit = None
            row.unit_qty = None
        else:
            row.grams = item.grams
            row.kcal_100g, row.protein_100g, row.carbs_100g, row.fat_100g = (
                src.kcal_100g, src.protein_100g, src.carbs_100g, src.fat_100g,
            )
            row.ingredients = None  # JSON null, like POST /food-logs (not SQL NULL)
            row.unit = item.unit
            row.unit_qty = item.unit_qty
        new_rows.append(row)

    db.add_all(new_rows)
    await db.commit()
    for row in new_rows:
        await db.refresh(row)
    return new_rows


@router.post("/food-logs/delete-batch", response_model=FoodLogDeleteBatchResponse)
async def delete_food_logs_batch(
    req: FoodLogDeleteBatchRequest,
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> FoodLogDeleteBatchResponse:
    result = await db.execute(
        delete(FoodLog).where(FoodLog.id.in_(req.ids), FoodLog.uid == token["uid"])
    )
    await db.commit()
    return FoodLogDeleteBatchResponse(deleted=result.rowcount)


_MAX_SUMMARY_DAYS = 62


@router.get("/food-logs/summary", response_model=FoodLogSummaryResponse)
async def food_log_summary(
    to: date,
    from_: date = Query(alias="from"),
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> FoodLogSummaryResponse:
    if from_ > to:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="from must be <= to"
        )
    if (to - from_).days + 1 > _MAX_SUMMARY_DAYS:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=f"Range cannot exceed {_MAX_SUMMARY_DAYS} days",
        )
    rows = (
        await db.execute(
            select(FoodLog).where(
                FoodLog.uid == token["uid"],
                FoodLog.log_date >= from_,
                FoodLog.log_date <= to,
            )
        )
    ).scalars().all()

    counts: Dict[date, Dict[str, list]] = {}
    for row in rows:
        key = MEAL_KEYS.get(row.meal)
        if key is None:
            continue
        acc = counts.setdefault(row.log_date, {}).setdefault(key, [0, 0.0])
        acc[0] += 1
        acc[1] += row.kcal_100g * row.grams / 100

    days = [
        DaySummary(
            date=d,
            meals={k: MealSummary(count=v[0], kcal=round(v[1])) for k, v in meals.items()},
        )
        for d, meals in sorted(counts.items(), key=lambda kv: kv[0], reverse=True)
    ]
    return FoodLogSummaryResponse(days=days)


@router.patch("/food-logs/by-client/{client_id}", response_model=FoodLogDto)
async def update_food_log_by_client(
    client_id: str,
    req: FoodLogUpdate,
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> FoodLog:
    row = await _find_by_client_id(db, token["uid"], client_id)
    if row is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Food log not found")
    _apply_update(row, req)
    await db.commit()
    await db.refresh(row)
    return row


@router.delete("/food-logs/by-client/{client_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_food_log_by_client(
    client_id: str,
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> Response:
    row = await _find_by_client_id(db, token["uid"], client_id)
    if row is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Food log not found")
    await db.delete(row)
    await db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.patch("/food-logs/{log_id}", response_model=FoodLogDto)
async def update_food_log(
    log_id: int,
    req: FoodLogUpdate,
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> FoodLog:
    uid = token["uid"]
    result = await db.execute(select(FoodLog).where(FoodLog.id == log_id))
    row = result.scalar_one_or_none()
    if row is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Food log not found")
    if row.uid != uid:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Not your food log")
    _apply_update(row, req)
    await db.commit()
    await db.refresh(row)
    return row


@router.delete("/food-logs/{log_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_food_log(
    log_id: int,
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> Response:
    uid = token["uid"]
    result = await db.execute(select(FoodLog).where(FoodLog.id == log_id))
    row = result.scalar_one_or_none()
    if row is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Food log not found")
    if row.uid != uid:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Not your food log")
    await db.delete(row)
    await db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
