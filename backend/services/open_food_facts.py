from __future__ import annotations

import httpx

from schemas import FoodItemDto

USER_AGENT = "FoodTracker-Licenta/0.1 (mihaipleanta@gmail.com)"
SEARCH_URL = "https://world.openfoodfacts.org/cgi/search.pl"
PRODUCT_URL = "https://world.openfoodfacts.org/api/v2/product/{barcode}"
FIELDS = "code,product_name,product_name_ro,brands,image_small_url,categories_tags,nutriments"


class OpenFoodFactsClient:
    def __init__(self, http_client: httpx.AsyncClient | None = None) -> None:
        self._http = http_client or httpx.AsyncClient(
            timeout=5.0,
            headers={"User-Agent": USER_AGENT},
        )

    async def search(self, query: str, page_size: int) -> list[FoodItemDto]:
        params = {
            "search_terms": query,
            "search_simple": 1,
            "action": "process",
            "json": 1,
            "page_size": page_size,
            "lc": "ro",
            "fields": FIELDS,
        }
        response = await self._http.get(SEARCH_URL, params=params)
        response.raise_for_status()
        products = response.json().get("products") or []
        normalized = (self._normalize(p) for p in products if isinstance(p, dict))
        return [item for item in normalized if item is not None]

    async def get_by_barcode(self, barcode: str) -> FoodItemDto | None:
        response = await self._http.get(
            PRODUCT_URL.format(barcode=barcode),
            params={"fields": FIELDS, "lc": "ro"},
        )
        response.raise_for_status()
        body = response.json()
        if body.get("status") != 1:
            return None
        product = body.get("product") or {}
        return self._normalize(product)

    def _normalize(self, product: dict) -> FoodItemDto | None:
        barcode = product.get("code")
        if not barcode:
            return None

        nutriments = product.get("nutriments") or {}
        required = ("energy-kcal_100g", "proteins_100g", "carbohydrates_100g", "fat_100g")
        if any(nutriments.get(k) is None for k in required):
            return None

        name = (
            product.get("product_name_ro")
            or product.get("product_name")
            or f"Product {barcode}"
        )

        raw_tags = product.get("categories_tags") or []
        categories: list[str] = []
        for tag in raw_tags:
            if not isinstance(tag, str):
                continue
            stripped = tag.split(":", 1)[1] if ":" in tag else tag
            if stripped not in categories:
                categories.append(stripped)
            if len(categories) == 3:
                break

        brands = product.get("brands")
        brand = brands.split(",")[0].strip() if brands else None

        return FoodItemDto(
            barcode=str(barcode),
            name=name,
            brand=brand or None,
            image_url=product.get("image_small_url") or None,
            kcal_100g=float(nutriments["energy-kcal_100g"]),
            protein_100g=float(nutriments["proteins_100g"]),
            carbs_100g=float(nutriments["carbohydrates_100g"]),
            fat_100g=float(nutriments["fat_100g"]),
            categories=categories,
        )


_singleton: OpenFoodFactsClient | None = None


def get_off_client() -> OpenFoodFactsClient:
    """FastAPI dependency. Used as Depends(get_off_client) — in tests via dependency_overrides."""
    global _singleton
    if _singleton is None:
        _singleton = OpenFoodFactsClient()
    return _singleton
