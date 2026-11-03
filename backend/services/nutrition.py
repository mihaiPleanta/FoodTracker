from dataclasses import dataclass
from decimal import Decimal, ROUND_HALF_UP
from typing import Literal, Optional, Protocol


_ACTIVITY_MULTIPLIERS = {
    "SEDENTARY": 1.2,
    "LIGHT": 1.375,
    "MODERATE": 1.55,
    "ACTIVE": 1.725,
    "VERY_ACTIVE": 1.9,
}

_HYDRATION_ML_PER_KG = 35
_CREATINE_BONUS_L = Decimal("0.5")
_HYDRATION_MIN_L = Decimal("1.5")
_HYDRATION_MAX_L = Decimal("5.0")
_HYDRATION_ACTIVITY_BONUS_L = {
    "SEDENTARY": Decimal("0"),
    "LIGHT": Decimal("0.25"),
    "MODERATE": Decimal("0.5"),
    "ACTIVE": Decimal("0.75"),
    "VERY_ACTIVE": Decimal("1.0"),
}
_HYDRATION_DEFAULT_ACTIVITY_BONUS_L = Decimal("0.5")

GoalMode = Literal["DEFICIT", "MAINTENANCE", "SURPLUS"]


@dataclass(frozen=True)
class Goals:
    calorie_goal: int
    protein_goal_g: int
    carbs_goal_g: int
    fat_goal_g: int
    mode: GoalMode
    tdee: int


class _ProfileLike(Protocol):
    age: int
    gender: str
    height_cm: int
    current_weight_kg: float
    target_weight_kg: float
    activity_level: str
    takes_creatine: bool
    hydration_goal_override_l: Optional[float]


@dataclass(frozen=True)
class HydrationGoal:
    goal_l: float
    auto_l: float
    is_manual: bool
    weight_part_l: float
    activity_bonus_l: float
    creatine_bonus_l: float


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
        tdee=round(tdee),
    )


def calculate_hydration_goal(profile: _ProfileLike) -> HydrationGoal:
    weight_part = (
        Decimal(str(profile.current_weight_kg)) * _HYDRATION_ML_PER_KG / 1000
    )
    activity_bonus = _HYDRATION_ACTIVITY_BONUS_L.get(
        profile.activity_level, _HYDRATION_DEFAULT_ACTIVITY_BONUS_L
    )
    creatine_bonus = _CREATINE_BONUS_L if profile.takes_creatine else Decimal("0")

    raw = weight_part + activity_bonus + creatine_bonus
    rounded = raw.quantize(Decimal("0.1"), rounding=ROUND_HALF_UP)
    auto = max(_HYDRATION_MIN_L, min(rounded, _HYDRATION_MAX_L))

    override = profile.hydration_goal_override_l
    is_manual = override is not None
    goal = Decimal(str(override)) if is_manual else auto

    return HydrationGoal(
        goal_l=float(goal),
        auto_l=float(auto),
        is_manual=is_manual,
        weight_part_l=float(weight_part.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)),
        activity_bonus_l=float(activity_bonus),
        creatine_bonus_l=float(creatine_bonus),
    )
