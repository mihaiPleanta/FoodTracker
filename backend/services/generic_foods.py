from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path

from schemas import FoodItemDto
from services.text_match import relevance

_DATA_PATH = Path(__file__).resolve().parent.parent / "data" / "generic_foods.json"

# Matches with a relevance bucket above this are considered too weak and dropped.
# Buckets: 0 category, 1 exact, 2 prefix, 3 substring, 4 token-subset, 5 token-overlap, 6 none.
_MATCH_THRESHOLD = 4


@dataclass(frozen=True)
class GenericFood:
    id: str
    name_ro: str
    aliases: list[str] = field(default_factory=list)
    categories: list[str] = field(default_factory=list)
    kcal_100g: float = 0.0
    protein_100g: float = 0.0
    carbs_100g: float = 0.0
    fat_100g: float = 0.0

    def to_dto(self) -> FoodItemDto:
        return FoodItemDto(
            barcode=self.id,
            name=self.name_ro,
            brand=None,
            image_url=None,
            kcal_100g=float(self.kcal_100g),
            protein_100g=float(self.protein_100g),
            carbs_100g=float(self.carbs_100g),
            fat_100g=float(self.fat_100g),
            categories=list(self.categories),
        )

    def best_rank(self, query: str) -> tuple[int, int]:
        """Best (lowest) relevance tuple across the RO name and all aliases."""
        candidates = [self.name_ro, *self.aliases]
        return min(relevance(c, self.categories, query) for c in candidates)


def _load_dataset(path: Path = _DATA_PATH) -> list[GenericFood]:
    """Load and validate the bundled dataset. Fails fast on a missing/corrupt file."""
    raw = json.loads(path.read_text(encoding="utf-8"))
    return [
        GenericFood(
            id=row["id"],
            name_ro=row["name_ro"],
            aliases=list(row.get("aliases", [])),
            categories=list(row.get("categories", [])),
            kcal_100g=row["kcal_100g"],
            protein_100g=row["protein_100g"],
            carbs_100g=row["carbs_100g"],
            fat_100g=row["fat_100g"],
        )
        for row in raw
    ]


class GenericFoodsClient:
    def __init__(self, foods: list[GenericFood] | None = None) -> None:
        self._foods = foods if foods is not None else _load_dataset()

    def search(self, query: str, limit: int) -> list[FoodItemDto]:
        scored: list[tuple[tuple[int, int], GenericFood]] = []
        for food in self._foods:
            rank = food.best_rank(query)
            if rank[0] <= _MATCH_THRESHOLD:
                scored.append((rank, food))
        scored.sort(key=lambda pair: pair[0])
        return [food.to_dto() for _, food in scored[:limit]]


_singleton: GenericFoodsClient | None = None


def get_generic_client() -> GenericFoodsClient:
    """FastAPI dependency. Used as Depends(get_generic_client) — overridable in tests."""
    global _singleton
    if _singleton is None:
        _singleton = GenericFoodsClient()
    return _singleton
