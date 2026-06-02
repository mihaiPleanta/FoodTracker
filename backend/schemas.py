from datetime import date as _date, datetime as _datetime
from typing import Dict, List, Literal, Optional

from pydantic import BaseModel, ConfigDict, Field


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


_MEAL_VALUES = Literal["BREAKFAST", "LUNCH", "DINNER", "SNACKS"]


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


class RecipeIngredient(BaseModel):
    name: str
    quantity: str


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


class RecipeGenerateRequest(BaseModel):
    meal_type: MealType


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
