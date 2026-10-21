from typing import List, Optional

from fastapi import APIRouter, Depends, Response, status
from pydantic import BaseModel, Field
from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from auth import verify_token
from database import get_db
from models import FavoriteFood
from schemas import FoodItemDto

router = APIRouter(prefix="/favorites", tags=["favorites"])


class FavoriteFoodIn(BaseModel):
    """Same wire shape as FoodItemDto, with non-negative macros enforced.
    The barcode in the body is ignored — the path parameter wins."""
    barcode: Optional[str] = None
    name: str
    brand: Optional[str] = None
    image_url: Optional[str] = None
    kcal_100g: float = Field(ge=0)
    protein_100g: float = Field(ge=0)
    carbs_100g: float = Field(ge=0)
    fat_100g: float = Field(ge=0)
    categories: List[str] = []


def _to_dto(row: FavoriteFood) -> FoodItemDto:
    return FoodItemDto.model_validate(row, from_attributes=True)


@router.get("", response_model=List[FoodItemDto])
async def list_favorites(
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> List[FoodItemDto]:
    result = await db.execute(
        select(FavoriteFood)
        .where(FavoriteFood.uid == token["uid"])
        .order_by(FavoriteFood.created_at.desc(), FavoriteFood.id.desc())
    )
    return [_to_dto(r) for r in result.scalars().all()]


@router.put("/{barcode}", response_model=FoodItemDto)
async def put_favorite(
    barcode: str,
    req: FavoriteFoodIn,
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> FoodItemDto:
    uid = token["uid"]
    result = await db.execute(
        select(FavoriteFood).where(FavoriteFood.uid == uid, FavoriteFood.barcode == barcode)
    )
    row = result.scalar_one_or_none()
    fields = req.model_dump(exclude={"barcode"})
    if row is None:
        row = FavoriteFood(uid=uid, barcode=barcode, **fields)
        db.add(row)
    else:
        for key, value in fields.items():
            setattr(row, key, value)
    await db.commit()
    await db.refresh(row)
    return _to_dto(row)


@router.delete("/{barcode}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_favorite(
    barcode: str,
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> Response:
    await db.execute(
        delete(FavoriteFood).where(
            FavoriteFood.uid == token["uid"], FavoriteFood.barcode == barcode
        )
    )
    await db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
