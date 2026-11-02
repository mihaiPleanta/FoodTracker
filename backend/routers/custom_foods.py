from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from auth import verify_token
from database import get_db
from models import CustomFood, UserPortionUnit
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


@router.post("", response_model=CustomFoodDto, status_code=status.HTTP_201_CREATED)
async def create_custom_food(
    req: CustomFoodIn,
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> CustomFoodDto:
    uid = token["uid"]
    if req.barcode is not None:
        existing = await find_by_barcode(db, uid, req.barcode)
        if existing is not None:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail={"code": "barcode_taken", "existing_id": existing.id},
            )
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
    db.add(row)
    await db.flush()
    serving = None
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
