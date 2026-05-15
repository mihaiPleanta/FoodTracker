from fastapi import APIRouter, Depends
from auth import verify_token
from schemas import TdeeRequest, TdeeResponse

router = APIRouter()

_ACTIVITY_MULTIPLIERS = {
    "SEDENTARY": 1.2,
    "LIGHT": 1.375,
    "MODERATE": 1.55,
    "ACTIVE": 1.725,
    "VERY_ACTIVE": 1.9,
}


def _mifflin_st_jeor(req: TdeeRequest) -> int:
    if req.gender == "MALE":
        bmr = 10 * req.current_weight_kg + 6.25 * req.height_cm - 5 * req.age + 5
    elif req.gender == "FEMALE":
        bmr = 10 * req.current_weight_kg + 6.25 * req.height_cm - 5 * req.age - 161
    else:
        bmr = 10 * req.current_weight_kg + 6.25 * req.height_cm - 5 * req.age - 78
    return round(bmr * _ACTIVITY_MULTIPLIERS.get(req.activity_level, 1.55))


@router.post("/calculate-tdee", response_model=TdeeResponse)
async def calculate_tdee(
    req: TdeeRequest,
    _token: dict = Depends(verify_token),
) -> TdeeResponse:
    return TdeeResponse(calorie_goal=_mifflin_st_jeor(req))
