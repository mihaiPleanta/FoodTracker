import re

from fastapi import APIRouter, Depends, HTTPException, Path, Query, status

from auth import verify_token
from schemas import FoodItemDto, SearchResponseDto
from services.open_food_facts import OpenFoodFactsClient, get_off_client

router = APIRouter(prefix="/foods", tags=["foods"])

_BARCODE_RE = re.compile(r"^\d{8,13}$")


@router.get("/search", response_model=SearchResponseDto)
async def search_foods(
    q: str = Query(..., min_length=2, max_length=50),
    page_size: int = Query(20, ge=1, le=50),
    off: OpenFoodFactsClient = Depends(get_off_client),
    token: dict = Depends(verify_token),
) -> SearchResponseDto:
    items = await off.search(q, page_size)
    return SearchResponseDto(items=items, count=len(items))


@router.get("/barcode/{barcode}", response_model=FoodItemDto)
async def get_food_by_barcode(
    barcode: str = Path(..., min_length=8, max_length=13),
    off: OpenFoodFactsClient = Depends(get_off_client),
    token: dict = Depends(verify_token),
) -> FoodItemDto:
    if not _BARCODE_RE.match(barcode):
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Invalid barcode format")
    item = await off.get_by_barcode(barcode)
    if item is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Product not found or nutrition data missing",
        )
    return item
