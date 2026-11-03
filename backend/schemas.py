import re
from datetime import date as _date, datetime as _datetime
from decimal import ROUND_HALF_UP, Decimal
from typing import Dict, List, Literal, Optional

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


class ProfileRequest(BaseModel):
    name: str
    age: int
    gender: str           # "MALE" | "FEMALE" | "OTHER"
    height_cm: int
    current_weight_kg: float
    target_weight_kg: float
    activity_level: str   # "SEDENTARY" | "LIGHT" | "MODERATE" | "ACTIVE" | "VERY_ACTIVE"
    takes_creatine: bool = False
    hydration_goal_override_l: Optional[float] = None

    @field_validator("hydration_goal_override_l")
    @classmethod
    def _validate_hydration_override(cls, v: Optional[float]) -> Optional[float]:
        if v is None:
            return None
        if v < 1.0 or v > 6.0:
            raise ValueError("hydration_goal_override_l must be between 1.0 and 6.0")
        return float(Decimal(str(v)).quantize(Decimal("0.1"), ROUND_HALF_UP))


class ProfileResponse(ProfileRequest):
    model_config = ConfigDict(from_attributes=True)
    uid: str


class TdeeRequest(BaseModel):
    age: int
    gender: str
    height_cm: int
    current_weight_kg: float
    activity_level: str


class TdeeResponse(BaseModel):
    calorie_goal: int


class FoodItemDto(BaseModel):
    barcode: str
    name: str
    brand: Optional[str] = None
    image_url: Optional[str] = None
    kcal_100g: float
    protein_100g: float
    carbs_100g: float
    fat_100g: float
    categories: List[str] = []


class SearchResponseDto(BaseModel):
    items: List[FoodItemDto]
    count: int
    has_more: bool = False


class RecentFoodDto(BaseModel):
    food: FoodItemDto
    last_grams: int


_MEAL_VALUES = Literal["BREAKFAST", "LUNCH", "DINNER", "SNACKS"]
MealValue = _MEAL_VALUES


class FoodLogIngredient(BaseModel):
    name: str
    grams: float = Field(gt=0)
    kcal_100g: float = Field(ge=0)
    protein_100g: float = Field(ge=0)
    carbs_100g: float = Field(ge=0)
    fat_100g: float = Field(ge=0)


def _check_unit_pair(unit: Optional[str], unit_qty: Optional[float]) -> None:
    if unit is None and unit_qty is None:
        return
    if unit is None or unit_qty is None:
        raise ValueError("unit and unit_qty must be both set or both null")
    if unit_qty <= 0:
        raise ValueError("unit_qty must be > 0")
    if not 1 <= len(unit.strip()) <= 30:
        raise ValueError("unit must be 1-30 characters")


class FoodLogCreate(BaseModel):
    log_date: _date
    meal: _MEAL_VALUES
    grams: int = Field(gt=0)
    barcode: str
    name: str
    brand: Optional[str] = None
    image_url: Optional[str] = None
    categories: List[str] = []
    kcal_100g: float = Field(ge=0)
    protein_100g: float = Field(ge=0)
    carbs_100g: float = Field(ge=0)
    fat_100g: float = Field(ge=0)
    ingredients: Optional[List[FoodLogIngredient]] = None
    unit: Optional[str] = None
    unit_qty: Optional[float] = None

    @model_validator(mode="after")
    def _unit_pair(self):
        _check_unit_pair(self.unit, self.unit_qty)
        return self


class FoodLogUpdate(BaseModel):
    grams: int = Field(gt=0)
    ingredients: Optional[List[FoodLogIngredient]] = None
    unit: Optional[str] = None
    unit_qty: Optional[float] = None

    @model_validator(mode="after")
    def _unit_pair(self):
        _check_unit_pair(self.unit, self.unit_qty)
        return self


class FoodLogCopyItem(BaseModel):
    source_id: int
    grams: int = Field(gt=0)
    unit: Optional[str] = None
    unit_qty: Optional[float] = None
    ingredients: Optional[List[FoodLogIngredient]] = None

    @model_validator(mode="after")
    def _unit_pair(self):
        _check_unit_pair(self.unit, self.unit_qty)
        return self


class FoodLogCopyRequest(BaseModel):
    to_date: _date
    items: List[FoodLogCopyItem] = Field(min_length=1, max_length=100)


class FoodLogDeleteBatchRequest(BaseModel):
    ids: List[int] = Field(min_length=1, max_length=100)


class FoodLogDeleteBatchResponse(BaseModel):
    deleted: int


class FoodLogDto(FoodLogCreate):
    model_config = ConfigDict(from_attributes=True)
    id: int
    created_at: _datetime


class HydrationUpdate(BaseModel):
    liters: float = Field(ge=0, le=10)


class HydrationDto(BaseModel):
    model_config = ConfigDict(from_attributes=True, populate_by_name=True)
    date: _date = Field(alias="log_date")
    liters: float


class WeightCheckInCreate(BaseModel):
    date: _date
    weight_kg: float = Field(gt=0, lt=500)


class WeightCheckInDto(BaseModel):
    model_config = ConfigDict(from_attributes=True, populate_by_name=True)
    date: _date = Field(alias="log_date")
    weight_kg: float


class WeightCheckInListDto(BaseModel):
    items: List[WeightCheckInDto]


class DayStatusUpdate(BaseModel):
    complete: bool


class DayStatusResponse(BaseModel):
    date: _date
    status: Literal["complete", "incomplete"]


class DayResponse(BaseModel):
    foods_by_meal: Dict[str, List[FoodLogDto]]
    hydration_liters: float
    weight_check_in: Optional[WeightCheckInDto] = None
    status: Optional[Literal["complete", "incomplete"]] = None


MEAL_KEYS = {
    "BREAKFAST": "breakfast",
    "LUNCH": "lunch",
    "DINNER": "dinner",
    "SNACKS": "snacks",
}


class MealSummary(BaseModel):
    count: int
    kcal: int


class DaySummary(BaseModel):
    date: _date
    meals: Dict[str, MealSummary]


class FoodLogSummaryResponse(BaseModel):
    days: List[DaySummary]


class HydrationGoalDto(BaseModel):
    goal_l: float
    auto_l: float
    is_manual: bool
    weight_part_l: float
    activity_bonus_l: float
    creatine_bonus_l: float


class GoalsResponse(BaseModel):
    calorie_goal: int
    protein_goal_g: int
    carbs_goal_g: int
    fat_goal_g: int
    mode: Literal["DEFICIT", "MAINTENANCE", "SURPLUS"]
    hydration: HydrationGoalDto


MealType = Literal["BREAKFAST", "LUNCH", "DINNER", "SNACKS"]


class TopFood(BaseModel):
    name: str
    brand: Optional[str] = None
    kcal_100g: float
    protein_100g: float
    carbs_100g: float
    fat_100g: float
    categories: List[str] = []


class RecipeIngredient(BaseModel):
    name: str
    grams: float = Field(gt=0)
    kcal_100g: float = Field(ge=0)
    protein_100g: float = Field(ge=0)
    carbs_100g: float = Field(ge=0)
    fat_100g: float = Field(ge=0)


class RecipeDto(BaseModel):
    title: str
    description: str
    ingredients: List[RecipeIngredient]
    steps: List[str]
    servings: int
    kcal_per_serving: int
    protein_g: int
    carbs_g: int
    fat_g: int
    anchor: Optional[str] = None

    # Small LLMs frequently return macros as floats (e.g. protein_g: 12.5).
    # Round them to int so the wire stays integer (the Android DTO expects Int)
    # instead of rejecting an otherwise-valid recipe.
    @field_validator(
        "servings", "kcal_per_serving", "protein_g", "carbs_g", "fat_g",
        mode="before",
    )
    @classmethod
    def _round_floats_to_int(cls, v):
        if isinstance(v, float):
            return round(v)
        if isinstance(v, str):
            try:
                return round(float(v))
            except ValueError:
                return v
        return v


class RecipeGenerateRequest(BaseModel):
    meal_type: MealType
    language: Literal["ro", "en"] = "ro"
    exclude_anchor: Optional[str] = None


class SavedRecipeCreate(BaseModel):
    meal_type: MealType
    recipe: RecipeDto


class SavedRecipeDto(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    meal_type: str
    title: str
    recipe: RecipeDto
    created_at: _datetime


class PortionUnitIn(BaseModel):
    unit: str
    grams: float = Field(gt=0, le=5000)

    @field_validator("unit")
    @classmethod
    def _check_unit(cls, v: str) -> str:
        from services.portions import STANDARD_UNITS

        if v in STANDARD_UNITS:
            return v  # exact standard key: a gram correction for a standard unit
        v = v.strip()
        if not 1 <= len(v) <= 30:
            raise ValueError("unit must be 1-30 characters")
        if v.casefold() in STANDARD_UNITS:
            raise ValueError("custom unit name collides with a standard unit key")
        return v


class PortionUnitOut(BaseModel):
    unit: str
    grams: float


class GramPresetsIn(BaseModel):
    grams: List[int] = Field(min_length=1, max_length=5)

    @field_validator("grams")
    @classmethod
    def _check_values(cls, v: List[int]) -> List[int]:
        if any(not 1 <= g <= 5000 for g in v):
            raise ValueError("each value must be between 1 and 5000")
        return v


class GramPresetsOut(BaseModel):
    grams: List[int]


class PortionUnitDto(BaseModel):
    unit: str
    grams: float
    source: str


class LastPortionDto(BaseModel):
    unit: Optional[str] = None
    unit_qty: Optional[float] = None
    grams: int


class PortionsDto(BaseModel):
    units: List[PortionUnitDto]
    gram_presets: List[int]
    presets_custom: bool
    last: Optional[LastPortionDto] = None


_BARCODE_RE = re.compile(r"^[0-9]{8,13}$")


class ServingIn(BaseModel):
    unit: str = Field(min_length=1, max_length=30)
    grams: float = Field(gt=0, le=2000)

    @field_validator("unit", mode="before")
    @classmethod
    def _strip_unit(cls, v):
        return v.strip() if isinstance(v, str) else v


class ServingOut(BaseModel):
    unit: str
    grams: float


class CustomFoodIn(BaseModel):
    name: str = Field(min_length=1, max_length=100)
    brand: Optional[str] = Field(default=None, max_length=100)
    barcode: Optional[str] = None
    kcal_100g: float = Field(ge=0, le=900)
    protein_100g: float = Field(ge=0, le=100)
    carbs_100g: float = Field(ge=0, le=100)
    fat_100g: float = Field(ge=0, le=100)
    serving: Optional[ServingIn] = None

    @field_validator("name", mode="before")
    @classmethod
    def _strip_name(cls, v):
        return v.strip() if isinstance(v, str) else v

    @field_validator("brand", "barcode", mode="before")
    @classmethod
    def _blank_to_none(cls, v):
        if isinstance(v, str):
            v = v.strip()
            return v or None
        return v

    @field_validator("barcode")
    @classmethod
    def _check_barcode(cls, v):
        if v is not None and not _BARCODE_RE.match(v):
            raise ValueError("barcode must be 8-13 digits")
        return v

    @model_validator(mode="after")
    def _check_macro_sum(self):
        # Epsilon: decimal inputs like 0.2 + 85.4 + 14.4 sum to 100.00000000000001 in float.
        if self.protein_100g + self.carbs_100g + self.fat_100g > 100 + 0.001:
            raise ValueError("protein + carbs + fat must be <= 100 g per 100 g")
        return self


class CustomFoodDto(BaseModel):
    id: int
    key: str
    name: str
    brand: Optional[str] = None
    barcode: Optional[str] = None
    kcal_100g: float
    protein_100g: float
    carbs_100g: float
    fat_100g: float
    serving: Optional[ServingOut] = None


class StatsGoalsDto(BaseModel):
    calorie_goal: int
    protein_goal_g: int
    carbs_goal_g: int
    fat_goal_g: int
    tdee: int


class StatsDayDto(BaseModel):
    date: _date
    kcal: int
    protein_g: float
    carbs_g: float
    fat_g: float
    has_food: bool
    status: Optional[Literal["complete", "incomplete"]] = None
    goals: Optional[StatsGoalsDto] = None
    hydration_l: float


class StatsWeightDto(BaseModel):
    date: _date
    weight_kg: float


class TopFoodStatDto(BaseModel):
    key: str
    name: str
    brand: Optional[str] = None
    total_kcal: int
    total_protein_g: float
    days_count: int


class TopFoodsDto(BaseModel):
    by_kcal: List[TopFoodStatDto] = []
    by_protein: List[TopFoodStatDto] = []


class StatsDaysResponse(BaseModel):
    days: List[StatsDayDto]
    weights: List[StatsWeightDto]
    top_foods: TopFoodsDto
