from fastapi import APIRouter, Depends, Response, status
from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from auth import verify_token
from database import get_db
from models import UserGramPresets, UserPortionUnit
from schemas import GramPresetsIn, GramPresetsOut, PortionUnitIn, PortionUnitOut

router = APIRouter(prefix="/portions", tags=["portions"])


@router.put("/{barcode}/units", response_model=PortionUnitOut)
async def put_unit(
    barcode: str,
    req: PortionUnitIn,
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> PortionUnitOut:
    uid = token["uid"]
    key = req.unit.strip().casefold()
    result = await db.execute(
        select(UserPortionUnit).where(
            UserPortionUnit.uid == uid,
            UserPortionUnit.barcode == barcode,
            UserPortionUnit.unit_key == key,
        )
    )
    row = result.scalar_one_or_none()
    if row is None:
        row = UserPortionUnit(uid=uid, barcode=barcode, unit=req.unit, unit_key=key, grams=req.grams)
        db.add(row)
    else:
        row.unit = req.unit
        row.grams = req.grams
    await db.commit()
    return PortionUnitOut(unit=row.unit, grams=row.grams)


@router.delete("/{barcode}/units", status_code=status.HTTP_204_NO_CONTENT)
async def delete_unit(
    barcode: str,
    unit: str,
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> Response:
    await db.execute(
        delete(UserPortionUnit).where(
            UserPortionUnit.uid == token["uid"],
            UserPortionUnit.barcode == barcode,
            UserPortionUnit.unit_key == unit.strip().casefold(),
        )
    )
    await db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.put("/{barcode}/presets", response_model=GramPresetsOut)
async def put_presets(
    barcode: str,
    req: GramPresetsIn,
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> GramPresetsOut:
    uid = token["uid"]
    row = await db.get(UserGramPresets, (uid, barcode))
    if row is None:
        db.add(UserGramPresets(uid=uid, barcode=barcode, presets=req.grams))
    else:
        row.presets = req.grams
    await db.commit()
    return GramPresetsOut(grams=req.grams)


@router.delete("/{barcode}/presets", status_code=status.HTTP_204_NO_CONTENT)
async def delete_presets(
    barcode: str,
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> Response:
    await db.execute(
        delete(UserGramPresets).where(
            UserGramPresets.uid == token["uid"], UserGramPresets.barcode == barcode
        )
    )
    await db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
