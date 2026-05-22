from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from auth import verify_token
from database import get_db
from models import Profile
from schemas import GoalsResponse
from services.nutrition import calculate_goals

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
    return GoalsResponse(
        calorie_goal=goals.calorie_goal,
        protein_goal_g=goals.protein_goal_g,
        carbs_goal_g=goals.carbs_goal_g,
        fat_goal_g=goals.fat_goal_g,
        mode=goals.mode,
    )
