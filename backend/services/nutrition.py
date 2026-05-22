from dataclasses import dataclass
from typing import Literal, Protocol


_ACTIVITY_MULTIPLIERS = {
    "SEDENTARY": 1.2,
    "LIGHT": 1.375,
    "MODERATE": 1.55,
    "ACTIVE": 1.725,
    "VERY_ACTIVE": 1.9,
}

GoalMode = Literal["DEFICIT", "MAINTENANCE", "SURPLUS"]


@dataclass(frozen=True)
class Goals:
    calorie_goal: int
    protein_goal_g: int
    carbs_goal_g: int
    fat_goal_g: int
    mode: GoalMode


class _ProfileLike(Protocol):
    age: int
    gender: str
    height_cm: int
    current_weight_kg: float
    target_weight_kg: float
    activity_level: str


def mifflin_st_jeor(*, age: int, gender: str, height_cm: int, weight_kg: float) -> float:
    base = 10 * weight_kg + 6.25 * height_cm - 5 * age
    if gender == "MALE":
        return base + 5
    if gender == "FEMALE":
        return base - 161
    return base - 78  # OTHER


def calculate_goals(profile: _ProfileLike) -> Goals:
    bmr = mifflin_st_jeor(
        age=profile.age,
        gender=profile.gender,
        height_cm=profile.height_cm,
        weight_kg=profile.current_weight_kg,
    )
    multiplier = _ACTIVITY_MULTIPLIERS.get(profile.activity_level, 1.55)
    tdee = bmr * multiplier

    diff = profile.target_weight_kg - profile.current_weight_kg
    if diff < -1:
        calorie_goal_raw = tdee - 500
        mode: GoalMode = "DEFICIT"
    elif diff > 1:
        calorie_goal_raw = tdee + 300
        mode = "SURPLUS"
    else:
        calorie_goal_raw = tdee
        mode = "MAINTENANCE"

    calorie_goal = max(1200, min(round(calorie_goal_raw), 5000))

    protein_goal_g = round(1.8 * profile.current_weight_kg)
    fat_kcal = 0.25 * calorie_goal
    fat_goal_g = round(fat_kcal / 9)
    remaining_kcal = calorie_goal - (protein_goal_g * 4) - fat_kcal
    carbs_goal_g = max(0, round(remaining_kcal / 4))

    return Goals(
        calorie_goal=calorie_goal,
        protein_goal_g=protein_goal_g,
        carbs_goal_g=carbs_goal_g,
        fat_goal_g=fat_goal_g,
        mode=mode,
    )
