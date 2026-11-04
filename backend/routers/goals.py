from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from auth import verify_token
from database import get_db
from models import Profile
from schemas import GoalsResponse, HydrationGoalDto
from services.nutrition import calculate_goals, calculate_hydration_goal

router = APIRouter()


@router.get("/goals", response_model=GoalsResponse)
async def get_goals(
    token: dict = Depends(verify_token),
    db: AsyncSession = Depends(get_db),
) -> GoalsResponse:
    uid = token["uid"]
    result = await db.execute(select(Profile).where(Profile.uid == uid))
    profile = result.scalar_one_or_none()
    if profile is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Profile not found")
    goals = calculate_goals(profile)
    hydration = calculate_hydration_goal(profile)
    return GoalsResponse(
        calorie_goal=goals.calorie_goal,
        protein_goal_g=goals.protein_goal_g,
        carbs_goal_g=goals.carbs_goal_g,
        fat_goal_g=goals.fat_goal_g,
        mode=goals.mode,
        tdee=goals.tdee,
        hydration=HydrationGoalDto(
            goal_l=hydration.goal_l,
            auto_l=hydration.auto_l,
            is_manual=hydration.is_manual,
            weight_part_l=hydration.weight_part_l,
            activity_bonus_l=hydration.activity_bonus_l,
            creatine_bonus_l=hydration.creatine_bonus_l,
        ),
    )
