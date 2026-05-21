from datetime import date

from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from auth import verify_token
from database import get_db
from models import HydrationLog
from schemas import HydrationDto, HydrationUpdate

router = APIRouter()


@router.put(
    "/hydration/{log_date}",
    response_model=HydrationDto,
    response_model_by_alias=False,
)
async def put_hydration(
    log_date: date,
    req: HydrationUpdate,
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> HydrationLog:
    uid = token["uid"]
    result = await db.execute(
        select(HydrationLog).where(
            HydrationLog.uid == uid,
            HydrationLog.log_date == log_date,
        )
    )
    row = result.scalar_one_or_none()
    if row is None:
        row = HydrationLog(uid=uid, log_date=log_date, liters=req.liters)
        db.add(row)
    else:
        row.liters = req.liters
    await db.commit()
    await db.refresh(row)
    return row
