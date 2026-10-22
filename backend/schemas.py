from datetime import date as _date, datetime as _datetime
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


class DayResponse(BaseModel):
    foods_by_meal: Dict[str, List[FoodLogDto]]
    hydration_liters: float
    weight_check_in: Optional[WeightCheckInDto] = None


class GoalsResponse(BaseModel):
    calorie_goal: int
    protein_goal_g: int
    carbs_goal_g: int
    fat_goal_g: int
    mode: Literal["DEFICIT", "MAINTENANCE", "SURPLUS"]


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
