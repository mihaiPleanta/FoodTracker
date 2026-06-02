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


async def test_generate_recipe_retries_then_succeeds():
    class _FlakyOllama:
        def __init__(self):
            self.calls = 0

        async def generate_json(self, messages):
            self.calls += 1
            if self.calls == 1:
                return "not json at all"
            return json.dumps(_RECIPE_JSON)

    fake = _FlakyOllama()
    async with SessionLocal() as db:
        db.add(_seed())
        db.add_all([_log("Pui", 100), _log("Orez", 100), _log("Mar", 100)])
        await db.commit()
        recipe = await generate_recipe(db, "u1", "LUNCH", fake)

    assert recipe.title == "Omletă cu brânză"
    assert fake.calls == 2  # first attempt failed, second recovered


from fastapi.testclient import TestClient

from main import app
from services.ollama_client import get_ollama_client

AUTH_HEADER = {"Authorization": "Bearer fake-test-token"}
_PROFILE_BODY = {
    "name": "Mihai", "age": 22, "gender": "MALE", "height_cm": 180,
    "current_weight_kg": 78.0, "target_weight_kg": 75.0, "activity_level": "MODERATE",
}


def _log_body(name):
    return {
        "log_date": "2026-06-01", "meal": "BREAKFAST", "grams": 100,
        "barcode": "000", "name": name, "categories": [],
        "kcal_100g": 100.0, "protein_100g": 5.0, "carbs_100g": 10.0, "fat_100g": 2.0,
    }


@pytest.fixture
def http():
    return TestClient(app)


@pytest.fixture
def override_ollama():
    fake = _FakeOllama(content=json.dumps(_RECIPE_JSON))
    app.dependency_overrides[get_ollama_client] = lambda: fake
    yield fake
    app.dependency_overrides.pop(get_ollama_client, None)


def _setup_user(http, n_foods=3):
    http.post("/profile", json=_PROFILE_BODY, headers=AUTH_HEADER)
    for name in ["Pui", "Orez", "Mar", "Cartof"][:n_foods]:
        http.post("/food-logs", json=_log_body(name), headers=AUTH_HEADER)


def test_generate_endpoint_returns_recipe(http, override_ollama):
    _setup_user(http)
    r = http.post("/recipes/generate", json={"meal_type": "LUNCH"}, headers=AUTH_HEADER)
    assert r.status_code == 200, r.text
    assert r.json()["title"] == "Omletă cu brânză"


def test_generate_endpoint_insufficient_returns_422(http, override_ollama):
    _setup_user(http, n_foods=2)
    r = http.post("/recipes/generate", json={"meal_type": "LUNCH"}, headers=AUTH_HEADER)
    assert r.status_code == 422


def test_generate_endpoint_ollama_down_returns_503(http):
    fake = _FakeOllama(error=OllamaUnavailable("down"))
    app.dependency_overrides[get_ollama_client] = lambda: fake
    try:
        _setup_user(http)
        r = http.post("/recipes/generate", json={"meal_type": "LUNCH"}, headers=AUTH_HEADER)
        assert r.status_code == 503
    finally:
        app.dependency_overrides.pop(get_ollama_client, None)


def test_generate_endpoint_bad_json_returns_502(http):
    fake = _FakeOllama(content="not json")
    app.dependency_overrides[get_ollama_client] = lambda: fake
    try:
        _setup_user(http)
        r = http.post("/recipes/generate", json={"meal_type": "LUNCH"}, headers=AUTH_HEADER)
        assert r.status_code == 502
    finally:
        app.dependency_overrides.pop(get_ollama_client, None)


def test_save_list_delete_recipe(http):
    http.post("/profile", json=_PROFILE_BODY, headers=AUTH_HEADER)
    save_body = {"meal_type": "DINNER", "recipe": _RECIPE_JSON}
    created = http.post("/recipes", json=save_body, headers=AUTH_HEADER)
    assert created.status_code == 201, created.text
    rid = created.json()["id"]

    listed = http.get("/recipes", headers=AUTH_HEADER)
    assert listed.status_code == 200
    assert any(item["id"] == rid for item in listed.json())

    deleted = http.delete(f"/recipes/{rid}", headers=AUTH_HEADER)
    assert deleted.status_code == 204
    assert http.delete(f"/recipes/{rid}", headers=AUTH_HEADER).status_code == 404


def test_delete_recipe_other_user_returns_403(http, mock_firebase_token):
    http.post("/profile", json=_PROFILE_BODY, headers=AUTH_HEADER)
    created = http.post(
        "/recipes", json={"meal_type": "DINNER", "recipe": _RECIPE_JSON}, headers=AUTH_HEADER
    ).json()
    rid = created["id"]
    mock_firebase_token.return_value = {"uid": "other-uid", "email": "x@y.com"}
    http.post("/profile", json=_PROFILE_BODY, headers=AUTH_HEADER)
    r = http.delete(f"/recipes/{rid}", headers=AUTH_HEADER)
    assert r.status_code == 403
