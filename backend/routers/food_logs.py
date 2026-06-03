from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from auth import verify_token
from database import get_db
from models import FoodLog
from schemas import FoodLogCreate, FoodLogDto, FoodLogUpdate

router = APIRouter()


@router.post(
    "/food-logs",
    response_model=FoodLogDto,
    status_code=status.HTTP_201_CREATED,
)
async def create_food_log(
    req: FoodLogCreate,
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> FoodLog:
    uid = token["uid"]
    row = FoodLog(uid=uid, **req.model_dump())
    db.add(row)
    await db.commit()
    await db.refresh(row)
    return row


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
    row.grams = req.grams
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
