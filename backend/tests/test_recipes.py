import pytest
from datetime import date

from database import SessionLocal
from models import Profile, FoodLog
from schemas import RecipeDto
from services.recipes import get_top_foods

_PROFILE_KW = dict(
    name="Mihai", age=22, gender="MALE", height_cm=180,
    current_weight_kg=78.0, target_weight_kg=75.0, activity_level="MODERATE",
)


def _log(name, grams):
    return FoodLog(
        uid="u1", log_date=date.today(), meal="BREAKFAST", grams=grams,
        barcode="000", name=name, brand=None, image_url=None, categories=[],
        kcal_100g=100.0, protein_100g=5.0, carbs_100g=10.0, fat_100g=2.0,
    )


def test_recipe_json_schema_has_expected_fields():
    schema = RecipeDto.model_json_schema()
    props = schema["properties"]
    for field in (
        "title", "description", "ingredients", "steps",
        "servings", "kcal_per_serving", "protein_g", "carbs_g", "fat_g",
    ):
        assert field in props


async def test_get_top_foods_orders_by_frequency_then_grams():
    async with SessionLocal() as db:
        db.add(Profile(uid="u1", **_PROFILE_KW))
        db.add_all([_log("Pui", 100), _log("Pui", 100), _log("Pui", 100)])
        db.add_all([_log("Orez", 100), _log("Orez", 100)])
        db.add(_log("Mar", 100))
        await db.commit()

        top = await get_top_foods(db, "u1")

    names = [t.name for t in top]
    assert names[:3] == ["Pui", "Orez", "Mar"]
