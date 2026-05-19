from typing import List, Optional

from pydantic import BaseModel, ConfigDict


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


# Adăugat la finalul backend/schemas.py


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
