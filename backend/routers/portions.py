import re
from datetime import date
from typing import Optional

import httpx
from fastapi import APIRouter, Depends, Response, status
from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from auth import verify_token
from database import get_db
from models import FoodLog, UserGramPresets, UserPortionUnit
from schemas import (
    GramPresetsIn,
    GramPresetsOut,
    LastPortionDto,
    PortionsDto,
    PortionUnitDto,
    PortionUnitIn,
    PortionUnitOut,
)
from services.generic_foods import GenericFoodsClient, get_generic_client
from services.open_food_facts import OpenFoodFactsClient, get_off_client
from services.portions import (
    TABLE,
    ProductPortionInfo,
    compose_presets,
    label_units,
    merge_units,
)

_BARCODE_RE = re.compile(r"^\d{8,13}$")
_HISTORY_LIMIT = 300

router = APIRouter(prefix="/portions", tags=["portions"])


@router.get("/{barcode}", response_model=PortionsDto)
async def get_portions(
    barcode: str,
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
    off: OpenFoodFactsClient = Depends(get_off_client),
    generic: GenericFoodsClient = Depends(get_generic_client),
) -> PortionsDto:
    uid = token["uid"]

    info: Optional[ProductPortionInfo] = None
    generic_id: Optional[str] = None
    if _BARCODE_RE.match(barcode):
        try:
            info = await off.get_portion_info(barcode)
        except (httpx.HTTPError, ValueError):
            # ValueError: OFF answered 200 with a non-JSON body. Never 5xx here.
            info = None
    else:
        food = generic.get(barcode)
        if food is not None:
            generic_id = food.id
            info = ProductPortionInfo(categories=food.categories, serving_g=None, package_g=None)

    if info is not None:
        label = label_units(info)
        estimates, category_presets = TABLE.lookup(info.categories, generic_id)
    else:
        label, estimates, category_presets = [], [], []

    user_rows = (
        await db.execute(
            select(UserPortionUnit)
            .where(UserPortionUnit.uid == uid, UserPortionUnit.barcode == barcode)
            .order_by(UserPortionUnit.created_at, UserPortionUnit.id)
        )
    ).scalars().all()
    units = merge_units(label, estimates, [(r.unit, r.grams) for r in user_rows])

    preset_row = await db.get(UserGramPresets, (uid, barcode))
    custom = list(preset_row.presets) if preset_row is not None and preset_row.presets else None

    history = (
        await db.execute(
            select(FoodLog)
            .where(
                FoodLog.uid == uid,
                FoodLog.barcode == barcode,
            )
            .order_by(FoodLog.created_at.desc(), FoodLog.id.desc())
            .limit(_HISTORY_LIMIT)
        )
    ).scalars().all()
    # Recipe logs are dropped in Python: JSON None may be stored as JSON 'null'
    # and `json = json` is not valid on PostgreSQL (same approach as recent_foods).
    history = [h for h in history if h.ingredients is None]

    presets = compose_presets(
        [(h.grams, h.log_date) for h in history], category_presets, custom, date.today()
    )
    last = None
    if history:
        h = history[0]
        last = LastPortionDto(unit=h.unit, unit_qty=h.unit_qty, grams=h.grams)

    return PortionsDto(
        units=[PortionUnitDto(unit=u.unit, grams=u.grams, source=u.source) for u in units],
        gram_presets=presets,
        presets_custom=custom is not None,
        last=last,
    )


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
