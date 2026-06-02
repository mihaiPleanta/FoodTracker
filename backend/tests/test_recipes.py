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


import json

from services.recipes import (
    generate_recipe, InsufficientData, RecipeParseError,
)
from services.ollama_client import OllamaUnavailable

_RECIPE_JSON = {
    "title": "Omletă cu brânză",
    "description": "Rapidă și bogată în proteine.",
    "ingredients": [
        {"name": "Ouă", "quantity": "2 buc"},
        {"name": "Brânză", "quantity": "50 g"},
    ],
    "steps": ["Bate ouăle.", "Adaugă brânza.", "Prăjește 5 minute."],
    "servings": 1,
    "kcal_per_serving": 320,
    "protein_g": 24,
    "carbs_g": 3,
    "fat_g": 22,
}


class _FakeOllama:
    def __init__(self, content="", error=None):
        self.content = content
        self.error = error
        self.calls = []

    async def generate_json(self, messages):
        self.calls.append(messages)
        if self.error is not None:
            raise self.error
        return self.content


def _seed(db_uid="u1"):
    return Profile(uid=db_uid, **_PROFILE_KW)


async def test_generate_recipe_happy_path():
    fake = _FakeOllama(content=json.dumps(_RECIPE_JSON))
    async with SessionLocal() as db:
        db.add(_seed())
        db.add_all([_log("Pui", 100), _log("Orez", 100), _log("Mar", 100)])
        await db.commit()
        recipe = await generate_recipe(db, "u1", "LUNCH", fake)

    assert recipe.title == "Omletă cu brânză"
    assert recipe.kcal_per_serving == 320
    user_msg = fake.calls[0][-1]["content"]
    assert "Pui" in user_msg and "Orez" in user_msg and "Mar" in user_msg


async def test_generate_recipe_insufficient_data_raises():
    fake = _FakeOllama(content=json.dumps(_RECIPE_JSON))
    async with SessionLocal() as db:
        db.add(_seed())
        db.add_all([_log("Pui", 100), _log("Orez", 100)])
        await db.commit()
        with pytest.raises(InsufficientData):
            await generate_recipe(db, "u1", "LUNCH", fake)


async def test_generate_recipe_ollama_down_raises():
    fake = _FakeOllama(error=OllamaUnavailable("down"))
    async with SessionLocal() as db:
        db.add(_seed())
        db.add_all([_log("Pui", 100), _log("Orez", 100), _log("Mar", 100)])
        await db.commit()
        with pytest.raises(OllamaUnavailable):
            await generate_recipe(db, "u1", "LUNCH", fake)


async def test_generate_recipe_invalid_json_raises():
    fake = _FakeOllama(content="this is not json")
    async with SessionLocal() as db:
        db.add(_seed())
        db.add_all([_log("Pui", 100), _log("Orez", 100), _log("Mar", 100)])
        await db.commit()
        with pytest.raises(RecipeParseError):
            await generate_recipe(db, "u1", "LUNCH", fake)
