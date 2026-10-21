from types import SimpleNamespace

from services.recent_foods import pick_recent

AUTH_HEADER = {"Authorization": "Bearer fake-test-token"}

_VALID_PROFILE = {
    "name": "Mihai", "age": 22, "gender": "MALE", "height_cm": 180,
    "current_weight_kg": 78.0, "target_weight_kg": 75.0, "activity_level": "MODERATE",
}


def _row(barcode, meal, grams, ingredients=None):
    return SimpleNamespace(barcode=barcode, meal=meal, grams=grams, ingredients=ingredients)


# ── pure function (rows are given newest-first) ──────────────────────────────

def test_pick_recent_dedups_by_barcode_keeping_newest():
    rows = [_row("a", "LUNCH", 200), _row("a", "LUNCH", 100)]
    picked = pick_recent(rows, "LUNCH")
    assert [(r.barcode, r.grams) for r in picked] == [("a", 200)]


def test_pick_recent_current_meal_first():
    rows = [_row("x", "DINNER", 50), _row("b", "BREAKFAST", 30), _row("y", "LUNCH", 70)]
    picked = pick_recent(rows, "BREAKFAST")
    assert [r.barcode for r in picked] == ["b", "x", "y"]


def test_last_grams_prefers_current_meal():
    # "a" eaten most recently at DINNER (300g), earlier at BREAKFAST (120g)
    rows = [_row("a", "DINNER", 300), _row("a", "BREAKFAST", 120)]
    assert pick_recent(rows, "BREAKFAST")[0].grams == 120
    assert pick_recent(rows, "LUNCH")[0].grams == 300


def test_pick_recent_skips_recipes():
    rows = [_row("r1", "LUNCH", 350, ingredients=[{"name": "x"}]), _row("a", "LUNCH", 100)]
    assert [r.barcode for r in pick_recent(rows, "LUNCH")] == ["a"]


def test_pick_recent_limit():
    rows = [_row(f"b{i}", "LUNCH", 100) for i in range(30)]
    picked = pick_recent(rows, "LUNCH", limit=20)
    assert len(picked) == 20
    assert picked[0].barcode == "b0"


# ── endpoint ─────────────────────────────────────────────────────────────────

def _log(client, barcode, meal, grams, name=None, ingredients=None):
    body = {
        "log_date": "2026-10-21", "meal": meal, "grams": grams, "barcode": barcode,
        "name": name or barcode, "brand": None, "image_url": None, "categories": [],
        "kcal_100g": 100.0, "protein_100g": 1.0, "carbs_100g": 1.0, "fat_100g": 1.0,
    }
    if ingredients is not None:
        body["ingredients"] = ingredients
    r = client.post("/food-logs", json=body, headers=AUTH_HEADER)
    assert r.status_code == 201, r.text


def test_recent_empty_for_new_user(client):
    client.post("/profile", json=_VALID_PROFILE, headers=AUTH_HEADER)
    r = client.get("/foods/recent?meal=BREAKFAST", headers=AUTH_HEADER)
    assert r.status_code == 200
    assert r.json() == []


def test_recent_endpoint_orders_and_shapes(client):
    client.post("/profile", json=_VALID_PROFILE, headers=AUTH_HEADER)
    _log(client, "oats", "BREAKFAST", 80)
    _log(client, "rice", "LUNCH", 150)
    _log(client, "oats", "DINNER", 40)
    _log(client, "recipe-1", "BREAKFAST", 300, ingredients=[
        {"name": "ou", "grams": 100, "kcal_100g": 155, "protein_100g": 13, "carbs_100g": 1, "fat_100g": 11},
    ])
    r = client.get("/foods/recent?meal=BREAKFAST", headers=AUTH_HEADER)
    assert r.status_code == 200, r.text
    body = r.json()
    assert [it["food"]["barcode"] for it in body] == ["oats", "rice"]
    assert body[0]["last_grams"] == 80          # from BREAKFAST, not the newer DINNER 40
    assert body[0]["food"]["kcal_100g"] == 100.0
    assert body[1]["last_grams"] == 150


def test_recent_invalid_meal_422(client):
    r = client.get("/foods/recent?meal=ELEVENSES", headers=AUTH_HEADER)
    assert r.status_code == 422


def test_recent_isolated_between_users(client, mock_firebase_token):
    client.post("/profile", json=_VALID_PROFILE, headers=AUTH_HEADER)
    _log(client, "oats", "BREAKFAST", 80)
    mock_firebase_token.return_value = {"uid": "other-uid", "email": "o@test.com"}
    assert client.get("/foods/recent?meal=BREAKFAST", headers=AUTH_HEADER).json() == []
