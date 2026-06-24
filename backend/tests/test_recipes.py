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


def _log(name, grams, categories=None, meal="BREAKFAST"):
    return FoodLog(
        uid="u1", log_date=date.today(), meal=meal, grams=grams,
        barcode="000", name=name, brand=None, image_url=None,
        categories=categories or [],
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


def test_recipe_coerces_float_macros_to_int():
    # Small LLMs often emit macros as floats (protein_g: 12.5); these must be
    # accepted and rounded, not rejected.
    recipe = RecipeDto.model_validate({
        "title": "Test",
        "description": "x",
        "ingredients": [{"name": "Ou", "grams": 100, "kcal_100g": 150,
                         "protein_100g": 12, "carbs_100g": 1, "fat_100g": 10}],
        "steps": ["fa"],
        "servings": 1.0,
        "kcal_per_serving": 320.6,
        "protein_g": 12.5,
        "carbs_g": 3.2,
        "fat_g": 22.0,
    })
    assert recipe.kcal_per_serving == 321
    assert recipe.protein_g == 12  # round(12.5) banker's rounding -> 12
    assert recipe.fat_g == 22


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
        # sums to the top-level totals below: 320 kcal / 24 P / 3 C / 22 F
        {"name": "Ouă", "grams": 100, "kcal_100g": 200, "protein_100g": 14, "carbs_100g": 1, "fat_100g": 14},
        {"name": "Brânză", "grams": 50, "kcal_100g": 240, "protein_100g": 20, "carbs_100g": 4, "fat_100g": 16},
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
    # The prompt anchors on a single one of the user's frequent foods, so exactly
    # one of the logged names appears as the "ingredient-vedetă".
    user_msg = fake.calls[0][-1]["content"]
    assert "Ingredient-vedetă" in user_msg
    assert sum(name in user_msg for name in ("Pui", "Orez", "Mar")) == 1


async def test_generate_recipe_uses_english_prompt_when_language_en():
    fake = _FakeOllama(content=json.dumps(_RECIPE_JSON))
    async with SessionLocal() as db:
        db.add(_seed())
        db.add_all([_log("Pui", 100), _log("Orez", 100), _log("Mar", 100)])
        await db.commit()
        await generate_recipe(db, "u1", "LUNCH", fake, "en")

    system_msg = fake.calls[0][0]["content"]
    user_msg = fake.calls[0][-1]["content"]
    # English path selects the English system prompt + user content.
    assert "professional chef" in system_msg
    assert "Star ingredient" in user_msg
    assert "Ingredient-vedetă" not in user_msg


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


# ── Anchor variety + meal-fit ──────────────────────────────────────────────────

def test_topfood_and_recipe_have_new_fields():
    from schemas import TopFood, RecipeGenerateRequest, RecipeDto
    tf = TopFood(name="Pui", kcal_100g=1, protein_100g=1, carbs_100g=1, fat_100g=1)
    assert tf.categories == []
    req = RecipeGenerateRequest(meal_type="LUNCH")
    assert req.exclude_anchor is None
    assert "anchor" in RecipeDto.model_json_schema()["properties"]


def test_is_sweet_anchor_classifies():
    from services.recipes import _is_sweet_anchor
    from schemas import TopFood

    def tf(name, cats=None):
        return TopFood(name=name, kcal_100g=1, protein_100g=1, carbs_100g=1,
                       fat_100g=1, categories=cats or [])

    assert _is_sweet_anchor(tf("Banană")) is True
    assert _is_sweet_anchor(tf("Banana")) is True
    assert _is_sweet_anchor(tf("Iaurt", ["Fruits", "Desserts"])) is True
    assert _is_sweet_anchor(tf("Pui")) is False
    assert _is_sweet_anchor(tf("Piept de pui", ["Meat"])) is False
    assert _is_sweet_anchor(tf("Orez")) is False


async def test_get_top_foods_includes_categories():
    async with SessionLocal() as db:
        db.add(Profile(uid="u1", **_PROFILE_KW))
        db.add_all([_log("Banană", 100, ["Fruits"]),
                    _log("Banană", 100, ["Fruits"]),
                    _log("Pui", 100, ["Meat"])])
        await db.commit()
        top = await get_top_foods(db, "u1")
    banana = next(t for t in top if t.name == "Banană")
    assert "Fruits" in banana.categories


async def test_generate_skips_sweet_anchor_at_lunch():
    fake = _FakeOllama(content=json.dumps(_RECIPE_JSON))
    async with SessionLocal() as db:
        db.add(_seed())
        db.add_all([_log("Banană", 100, ["Fruits"]),
                    _log("Pui", 100, ["Meat"]),
                    _log("Orez", 100, ["Grains"])])
        await db.commit()
        for _ in range(10):
            recipe = await generate_recipe(db, "u1", "LUNCH", fake)
            assert recipe.anchor != "Banană"


async def test_generate_allows_sweet_anchor_at_breakfast():
    fake = _FakeOllama(content=json.dumps(_RECIPE_JSON))
    async with SessionLocal() as db:
        db.add(_seed())
        db.add_all([_log("Banană", 100, ["Fruits"]),
                    _log("Mar dulce", 100, ["Fruits"]),
                    _log("Capsuni", 100, ["Fruits"])])
        await db.commit()
        recipe = await generate_recipe(db, "u1", "BREAKFAST", fake)
    assert recipe.anchor in ("Banană", "Mar dulce", "Capsuni")


async def test_generate_falls_back_when_all_sweet_at_lunch():
    fake = _FakeOllama(content=json.dumps(_RECIPE_JSON))
    async with SessionLocal() as db:
        db.add(_seed())
        db.add_all([_log("Banană", 100, ["Fruits"]),
                    _log("Capsuni", 100, ["Fruits"]),
                    _log("Zmeura", 100, ["Fruits"])])
        await db.commit()
        recipe = await generate_recipe(db, "u1", "LUNCH", fake)
    assert recipe.anchor in ("Banană", "Capsuni", "Zmeura")


async def test_generate_excludes_previous_anchor_when_alternative_exists():
    fake = _FakeOllama(content=json.dumps(_RECIPE_JSON))
    async with SessionLocal() as db:
        db.add(_seed())
        db.add_all([_log("Pui", 100, ["Meat"]),
                    _log("Vita", 100, ["Meat"]),
                    _log("Orez", 100, ["Grains"])])
        await db.commit()
        for _ in range(10):
            recipe = await generate_recipe(db, "u1", "LUNCH", fake, exclude_anchor="Pui")
            assert recipe.anchor != "Pui"


def test_recipe_totals_recomputed_from_ingredients():
    from services.recipes import _recipe_totals
    from schemas import RecipeIngredient
    ings = [
        RecipeIngredient(name="Pui", grams=200, kcal_100g=165, protein_100g=31, carbs_100g=0, fat_100g=3.6),
        RecipeIngredient(name="Orez", grams=150, kcal_100g=130, protein_100g=2.7, carbs_100g=28, fat_100g=0.3),
    ]
    kcal, p, c, f = _recipe_totals(ings)
    assert kcal == round(165 * 2 + 130 * 1.5)   # 330 + 195 = 525
    assert p == round(31 * 2 + 2.7 * 1.5)


async def test_generate_recipe_sets_anchor_and_recomputed_totals():
    fake = _FakeOllama(content=json.dumps(_RECIPE_JSON))
    async with SessionLocal() as db:
        db.add(_seed())
        db.add_all([_log("Pui", 100), _log("Orez", 100), _log("Mar", 100)])
        await db.commit()
        recipe = await generate_recipe(db, "u1", "LUNCH", fake)
    assert recipe.kcal_per_serving == 320  # recomputed from _RECIPE_JSON ingredients
    assert recipe.anchor in ("Pui", "Orez", "Mar")


async def test_generate_recipe_scales_oversized_recipe_to_meal_budget():
    # A dish whose ingredients blow past the lunch budget must be scaled down so the
    # whole recipe is a single serving that fits the budget (user chose 1-portion).
    big = {
        "title": "Tocană uriașă", "description": "x",
        "ingredients": [
            {"name": "Cartofi", "grams": 1000, "kcal_100g": 100, "protein_100g": 2, "carbs_100g": 20, "fat_100g": 0},
            {"name": "Brânză", "grams": 200, "kcal_100g": 300, "protein_100g": 20, "carbs_100g": 2, "fat_100g": 24},
        ],
        "steps": ["fa"], "servings": 1, "kcal_per_serving": 0,
        "protein_g": 0, "carbs_g": 0, "fat_g": 0,
    }
    fake = _FakeOllama(content=json.dumps(big))
    async with SessionLocal() as db:
        db.add(_seed())
        db.add_all([_log("Pui", 100), _log("Orez", 100), _log("Mar", 100)])
        await db.commit()
        recipe = await generate_recipe(db, "u1", "LUNCH", fake)

    # The whole recipe (originally 1600 kcal) is one serving near the ~802 kcal lunch budget.
    assert recipe.servings == 1
    assert recipe.kcal_per_serving <= 810
    assert recipe.kcal_per_serving >= 750
    assert recipe.ingredients[0].grams < 1000   # potatoes scaled down


async def test_generate_recipe_leaves_within_budget_recipe_unscaled():
    # _RECIPE_JSON totals 320 kcal, well under the lunch budget → no scaling, servings 1.
    fake = _FakeOllama(content=json.dumps(_RECIPE_JSON))
    async with SessionLocal() as db:
        db.add(_seed())
        db.add_all([_log("Pui", 100), _log("Orez", 100), _log("Mar", 100)])
        await db.commit()
        recipe = await generate_recipe(db, "u1", "LUNCH", fake)
    assert recipe.servings == 1
    assert recipe.kcal_per_serving == 320
    assert recipe.ingredients[0].grams == 100   # unchanged


async def test_generate_keeps_anchor_when_it_is_the_only_option():
    fake = _FakeOllama(content=json.dumps(_RECIPE_JSON))
    async with SessionLocal() as db:
        db.add(_seed())
        db.add_all([_log("Pui", 100, ["Meat"]),
                    _log("Banană", 100, ["Fruits"]),
                    _log("Capsuni", 100, ["Fruits"])])
        await db.commit()
        recipe = await generate_recipe(db, "u1", "LUNCH", fake, exclude_anchor="Pui")
    assert recipe.anchor == "Pui"
