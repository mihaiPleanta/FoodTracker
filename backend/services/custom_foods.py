from __future__ import annotations

import unicodedata
from typing import Iterable, Optional

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from models import CustomFood
from schemas import FoodItemDto
from services.text_match import relevance

CUSTOM_PREFIX = "custom-"
_MATCH_THRESHOLD = 4


def custom_key(food_id: int) -> str:
    return f"{CUSTOM_PREFIX}{food_id}"


def parse_custom_key(key: str) -> Optional[int]:
    if not key.startswith(CUSTOM_PREFIX):
        return None
    rest = key[len(CUSTOM_PREFIX):]
    if not rest.isascii() or not rest.isdigit():
        return None
    return int(rest)


def normalize_name(name: str) -> str:
    decomposed = unicodedata.normalize("NFD", name.casefold())
    return "".join(c for c in decomposed if not unicodedata.combining(c))


def to_food_item(row: CustomFood) -> FoodItemDto:
    return FoodItemDto(
        barcode=custom_key(row.id),
        name=row.name,
        brand=row.brand,
        image_url=None,
        kcal_100g=row.kcal_100g,
        protein_100g=row.protein_100g,
        carbs_100g=row.carbs_100g,
        fat_100g=row.fat_100g,
        categories=[],
    )


async def find_by_barcode(db: AsyncSession, uid: str, barcode: str) -> Optional[CustomFood]:
    result = await db.execute(
        select(CustomFood).where(CustomFood.uid == uid, CustomFood.barcode == barcode)
    )
    return result.scalars().first()


async def load_for_keys(
    db: AsyncSession, uid: str, keys: Iterable[str]
) -> dict[str, CustomFood]:
    ids = {i for i in (parse_custom_key(k) for k in keys) if i is not None}
    if not ids:
        return {}
    result = await db.execute(
        select(CustomFood).where(CustomFood.uid == uid, CustomFood.id.in_(ids))
    )
    return {custom_key(r.id): r for r in result.scalars().all()}


def apply_current(
    item: FoodItemDto, current: dict[str, CustomFood]
) -> Optional[FoodItemDto]:
    """Swap a stored snapshot for the product's current values.

    Non-custom keys pass through; custom keys missing from `current` (deleted)
    return None so the caller can drop them."""
    if parse_custom_key(item.barcode or "") is None:
        return item
    row = current.get(item.barcode)
    return to_food_item(row) if row is not None else None


async def search_custom(
    db: AsyncSession, uid: str, query: str, limit: int = 10
) -> list[FoodItemDto]:
    q = normalize_name(query)
    result = await db.execute(select(CustomFood).where(CustomFood.uid == uid))
    scored = []
    for row in result.scalars().all():
        name = normalize_name(row.name)
        score = relevance(name, [], q)
        if row.brand:
            score = min(score, relevance(normalize_name(f"{row.brand} {row.name}"), [], q))
        if score[0] <= _MATCH_THRESHOLD:
            scored.append((score, row))
    scored.sort(key=lambda t: t[0])
    return [to_food_item(r) for _, r in scored[:limit]]
