from contextlib import asynccontextmanager
from datetime import datetime, timezone
from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy import delete, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from auth import verify_token
from database import get_db
from models import CustomFood, FavoriteFood, UserGramPresets, UserPortionUnit
from schemas import CustomFoodDto, CustomFoodIn, ServingOut
from services.custom_foods import custom_key, find_by_barcode, normalize_name

router = APIRouter(prefix="/custom-foods", tags=["custom-foods"])


def _to_dto(row: CustomFood, serving: Optional[UserPortionUnit]) -> CustomFoodDto:
    return CustomFoodDto(
        id=row.id,
        key=custom_key(row.id),
        name=row.name,
        brand=row.brand,
        barcode=row.barcode,
        kcal_100g=row.kcal_100g,
        protein_100g=row.protein_100g,
        carbs_100g=row.carbs_100g,
        fat_100g=row.fat_100g,
        serving=ServingOut(unit=serving.unit, grams=serving.grams) if serving else None,
    )


@router.get("", response_model=List[CustomFoodDto])
async def list_custom_foods(
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> List[CustomFoodDto]:
    uid = token["uid"]
    rows = (
        await db.execute(select(CustomFood).where(CustomFood.uid == uid))
    ).scalars().all()
    units = (
        await db.execute(
            select(UserPortionUnit)
            .where(UserPortionUnit.uid == uid, UserPortionUnit.barcode.like("custom-%"))
            .order_by(UserPortionUnit.created_at, UserPortionUnit.id)
        )
    ).scalars().all()
    first_unit: dict[str, UserPortionUnit] = {}
    for u in units:
        first_unit.setdefault(u.barcode, u)
    rows = sorted(rows, key=lambda r: (normalize_name(r.name), r.id))
    return [_to_dto(r, first_unit.get(custom_key(r.id))) for r in rows]


async def _barcode_taken(db: AsyncSession, uid: str, barcode: str, own_id: Optional[int]) -> Optional[int]:
    existing = await find_by_barcode(db, uid, barcode)
    if existing is not None and existing.id != own_id:
        return existing.id
    return None


def _conflict(existing_id: int) -> HTTPException:
    return HTTPException(
        status_code=status.HTTP_409_CONFLICT,
        detail={"code": "barcode_taken", "existing_id": existing_id},
    )


@asynccontextmanager
async def _unique_barcode_guard(db: AsyncSession, uid: str, barcode: Optional[str], own_id: Optional[int]):
    """Wrap flush/commit; a unique-index violation (race) becomes the same 409 as the pre-check."""
    try:
        yield
    except IntegrityError:
        await db.rollback()
        existing_id = await _barcode_taken(db, uid, barcode, own_id) if barcode else None
        if existing_id is None:
            raise
        raise _conflict(existing_id)


async def _get_owned(db: AsyncSession, uid: str, food_id: int) -> CustomFood:
    row = (
        await db.execute(select(CustomFood).where(CustomFood.id == food_id, CustomFood.uid == uid))
    ).scalars().first()
    if row is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Custom food not found")
    return row


@router.post("", response_model=CustomFoodDto, status_code=status.HTTP_201_CREATED)
async def create_custom_food(
    req: CustomFoodIn,
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> CustomFoodDto:
    uid = token["uid"]
    if req.barcode is not None:
        taken = await _barcode_taken(db, uid, req.barcode, None)
        if taken is not None:
            raise _conflict(taken)
    row = CustomFood(
        uid=uid,
        name=req.name,
        brand=req.brand,
        barcode=req.barcode,
        kcal_100g=req.kcal_100g,
        protein_100g=req.protein_100g,
        carbs_100g=req.carbs_100g,
        fat_100g=req.fat_100g,
    )
    serving = None
    async with _unique_barcode_guard(db, uid, req.barcode, None):
        db.add(row)
        await db.flush()
        if req.serving is not None:
            serving = UserPortionUnit(
                uid=uid,
                barcode=custom_key(row.id),
                unit=req.serving.unit,
                unit_key=req.serving.unit.strip().casefold(),
                grams=req.serving.grams,
            )
            db.add(serving)
        await db.commit()
    await db.refresh(row)
    return _to_dto(row, serving)


@router.put("/{food_id}", response_model=CustomFoodDto)
async def update_custom_food(
    food_id: int,
    req: CustomFoodIn,
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> CustomFoodDto:
    uid = token["uid"]
    row = await _get_owned(db, uid, food_id)
    if req.barcode is not None:
        taken = await _barcode_taken(db, uid, req.barcode, row.id)
        if taken is not None:
            raise _conflict(taken)
    row.name = req.name
    row.brand = req.brand
    row.barcode = req.barcode
    row.kcal_100g = req.kcal_100g
    row.protein_100g = req.protein_100g
    row.carbs_100g = req.carbs_100g
    row.fat_100g = req.fat_100g
    row.updated_at = datetime.now(timezone.utc)
    key = custom_key(row.id)
    async with _unique_barcode_guard(db, uid, req.barcode, row.id):
        await db.commit()
    await db.refresh(row)
    serving = (
        await db.execute(
            select(UserPortionUnit)
            .where(UserPortionUnit.uid == uid, UserPortionUnit.barcode == key)
            .order_by(UserPortionUnit.created_at, UserPortionUnit.id)
        )
    ).scalars().first()
    return _to_dto(row, serving)


@router.delete("/{food_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_custom_food(
    food_id: int,
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> Response:
    uid = token["uid"]
    row = await _get_owned(db, uid, food_id)
    key = custom_key(row.id)
    for model in (UserPortionUnit, UserGramPresets, FavoriteFood):
        await db.execute(delete(model).where(model.uid == uid, model.barcode == key))
    await db.delete(row)
    await db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
