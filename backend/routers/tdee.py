from fastapi import APIRouter, Depends
from auth import verify_token
from schemas import TdeeRequest, TdeeResponse
from services.nutrition import mifflin_st_jeor, _ACTIVITY_MULTIPLIERS

router = APIRouter()


@router.post("/calculate-tdee", response_model=TdeeResponse)
async def calculate_tdee(
    req: TdeeRequest,
    _token: dict = Depends(verify_token),
) -> TdeeResponse:
    bmr = mifflin_st_jeor(
        age=req.age,
        gender=req.gender,
        height_cm=req.height_cm,
        weight_kg=req.current_weight_kg,
    )
    multiplier = _ACTIVITY_MULTIPLIERS.get(req.activity_level, 1.55)
    return TdeeResponse(calorie_goal=round(bmr * multiplier))
