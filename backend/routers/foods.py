import re

import httpx
from fastapi import APIRouter, Depends, HTTPException, Path, Query, status

from auth import verify_token
from schemas import FoodItemDto, SearchResponseDto
from services.generic_foods import GenericFoodsClient, get_generic_client
from services.open_food_facts import OpenFoodFactsClient, get_off_client

router = APIRouter(prefix="/foods", tags=["foods"])

_BARCODE_RE = re.compile(r"^\d{8,13}$")


def _translate_off_error(exc: httpx.HTTPStatusError) -> HTTPException:
    code = exc.response.status_code
    # 429 (rate limit) and OFF's transient 5xx → tell the user the DB is busy.
    if code == 429 or code >= 500:
        return HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Food database busy, try again",
        )
    return HTTPException(
        status_code=status.HTTP_502_BAD_GATEWAY,
        detail="Food database returned an error",
    )


@router.get("/search", response_model=SearchResponseDto)
async def search_foods(
    q: str = Query(..., min_length=2, max_length=50),
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=50),
    off: OpenFoodFactsClient = Depends(get_off_client),
    generic: GenericFoodsClient = Depends(get_generic_client),
    token: dict = Depends(verify_token),
) -> SearchResponseDto:
    # Genericele sunt locale (in-memory) — mereu disponibile. Le folosim pentru nume la
    # dedup pe orice pagină, dar le includem în output doar pe pagina 1.
    generics = generic.search(q, page_size)

    # OFF e live și poate pica. Pe pagina 1, dacă avem generice, degradăm grațios și
    # întoarcem doar genericele. Pe paginile 2+ (load-more) lăsăm eroarea să propage.
    try:
        off_items, off_total = await off.search(q, page_size, page)
    except httpx.HTTPStatusError as exc:
        if page > 1 or not generics:
            raise _translate_off_error(exc) from exc
        off_items, off_total = [], 0
    except httpx.RequestError as exc:
        if page > 1 or not generics:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Food database busy, try again",
            ) from exc
        off_items, off_total = [], 0

    # Drop OFF products that duplicate a generic by name (pe orice pagină).
    generic_names = {g.name.strip().lower() for g in generics}
    off_items = [it for it in off_items if it.name.strip().lower() not in generic_names]

    # Genericele se prepend doar pe pagina 1; paginile 2+ sunt doar OFF. Fără trim.
    prefix = generics if page == 1 else []
    items = prefix + off_items
    has_more = page * page_size < off_total
    return SearchResponseDto(items=items, count=len(items), has_more=has_more)


@router.get("/barcode/{barcode}", response_model=FoodItemDto)
async def get_food_by_barcode(
    barcode: str = Path(..., min_length=8, max_length=13),
    off: OpenFoodFactsClient = Depends(get_off_client),
    token: dict = Depends(verify_token),
) -> FoodItemDto:
    if not _BARCODE_RE.match(barcode):
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Invalid barcode format")
    try:
        item = await off.get_by_barcode(barcode)
    except httpx.HTTPStatusError as exc:
        raise _translate_off_error(exc) from exc
    except httpx.RequestError as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Food database busy, try again",
        ) from exc
    if item is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Product not found or nutrition data missing",
        )
    return item
