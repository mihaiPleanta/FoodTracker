from __future__ import annotations

import asyncio

import httpx

from schemas import FoodItemDto

USER_AGENT = "FoodTracker-Licenta/0.1 (mihaipleanta@gmail.com)"
SEARCH_URL = "https://world.openfoodfacts.org/cgi/search.pl"
PRODUCT_URL = "https://world.openfoodfacts.org/api/v2/product/{barcode}"
FIELDS = "code,product_name,product_name_ro,brands,image_small_url,categories_tags,nutriments"


class OpenFoodFactsClient:
    def __init__(self, http_client: httpx.AsyncClient | None = None) -> None:
        self._http = http_client or httpx.AsyncClient(
            timeout=10.0,
            headers={"User-Agent": USER_AGENT},
        )

    async def _get_with_retry(self, url: str, params: dict) -> httpx.Response:
        # OFF intermittently returns 5xx; up to 2 quick retries usually clears it.
        last_response: httpx.Response | None = None
        for attempt in range(3):
            response = await self._http.get(url, params=params)
            if response.status_code < 500 or response.status_code == 504:
                response.raise_for_status()
                return response
            last_response = response
            if attempt < 2:
                await asyncio.sleep(0.4 * (attempt + 1))
        assert last_response is not None
        last_response.raise_for_status()
        return last_response

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
        response = await self._get_with_retry(SEARCH_URL, params)
        products = response.json().get("products") or []
        normalized = (self._normalize(p) for p in products if isinstance(p, dict))
        items = [item for item in normalized if item is not None]
        # OFF returns results by popularity. Re-rank so the closest text/category match wins.
        items.sort(key=lambda it: _relevance(it.name, it.categories, query))
        return items

    async def get_by_barcode(self, barcode: str) -> FoodItemDto | None:
        response = await self._get_with_retry(
            PRODUCT_URL.format(barcode=barcode),
            {"fields": FIELDS, "lc": "ro"},
        )
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


def _stem(word: str) -> str:
    """Naive plural / language-variant trimmer: banana / banane / bananes / banana's → 'banan'.
    Stops at 4 chars to avoid over-stemming short words."""
    w = word.lower().strip().rstrip("'s")
    while len(w) > 4 and w[-1] in "aeios":
        w = w[:-1]
    return w


def _relevance(name: str, categories: list[str], query: str) -> tuple[int, int]:
    """Lower tuple wins.
    Order: category match (strongest signal of category fit) > exact name > name prefix >
    name substring > token-subset > token-overlap > other. Ties broken by shorter name."""
    n = name.lower().strip()
    q = query.lower().strip()
    q_stem = _stem(q)
    n_stem = _stem(n)
    cat_stems = [_stem(c.replace("-", " ").split()[-1]) for c in categories if c]

    n_tokens = set(n.split())
    q_tokens = {t for t in q.split() if t}

    if q_stem and q_stem in cat_stems:
        bucket = 0  # the product is literally in the query's category (e.g., "bananas")
    elif n == q:
        bucket = 1
    elif n_stem.startswith(q_stem) or n.startswith(q):
        bucket = 2
    elif q_stem in n_stem or q in n:
        bucket = 3
    elif q_tokens and q_tokens.issubset(n_tokens):
        bucket = 4
    elif n_tokens & q_tokens:
        bucket = 5
    else:
        bucket = 6
    return (bucket, len(name))


_singleton: OpenFoodFactsClient | None = None


def get_off_client() -> OpenFoodFactsClient:
    """FastAPI dependency. Used as Depends(get_off_client) — in tests via dependency_overrides."""
    global _singleton
    if _singleton is None:
        _singleton = OpenFoodFactsClient()
    return _singleton
