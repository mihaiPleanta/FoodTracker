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
    page_size: int = Query(20, ge=1, le=50),
    off: OpenFoodFactsClient = Depends(get_off_client),
    generic: GenericFoodsClient = Depends(get_generic_client),
    token: dict = Depends(verify_token),
) -> SearchResponseDto:
    # Generic foods come from a local in-memory dataset — always available.
    generics = generic.search(q, page_size)

    # Open Food Facts is live and may be down. If we already have generic matches,
    # degrade gracefully and return just those instead of failing the whole search.
    try:
        off_items = await off.search(q, page_size)
    except httpx.HTTPStatusError as exc:
        if not generics:
            raise _translate_off_error(exc) from exc
        off_items = []
    except httpx.RequestError as exc:
        if not generics:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Food database busy, try again",
            ) from exc
        off_items = []

    # Drop OFF products that duplicate a generic by name; generics always rank first.
    generic_names = {g.name.strip().lower() for g in generics}
    off_items = [it for it in off_items if it.name.strip().lower() not in generic_names]

    # page_size caps the response, but matching generics are never trimmed — only the
    # OFF tail is cut. (max(...) keeps every generic even if they alone exceed page_size.)
    items = (generics + off_items)[: max(page_size, len(generics))]
    return SearchResponseDto(items=items, count=len(items))


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
