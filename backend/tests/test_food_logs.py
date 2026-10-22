AUTH_HEADER = {"Authorization": "Bearer fake-test-token"}

_VALID_PROFILE = {
    "name": "Mihai",
    "age": 22,
    "gender": "MALE",
    "height_cm": 180,
    "current_weight_kg": 78.0,
    "target_weight_kg": 75.0,
    "activity_level": "MODERATE",
}

_VALID_LOG = {
    "log_date": "2026-05-20",
    "meal": "BREAKFAST",
    "grams": 150,
    "barcode": "3017620422003",
    "name": "Nutella",
    "brand": "Ferrero",
    "image_url": "https://example/nutella.jpg",
    "categories": ["Spreads"],
    "kcal_100g": 539.0,
    "protein_100g": 6.3,
    "carbs_100g": 57.5,
    "fat_100g": 30.9,
}


def _ensure_profile(client):
    client.post("/profile", json=_VALID_PROFILE, headers=AUTH_HEADER)


def test_create_food_log_returns_201(client):
    _ensure_profile(client)
    r = client.post("/food-logs", json=_VALID_LOG, headers=AUTH_HEADER)
    assert r.status_code == 201, r.text
    body = r.json()
    assert body["id"] > 0
    assert body["meal"] == "BREAKFAST"
    assert body["grams"] == 150
    assert body["name"] == "Nutella"
    assert body["kcal_100g"] == 539.0


def test_create_food_log_invalid_meal_returns_422(client):
    _ensure_profile(client)
    bad = {**_VALID_LOG, "meal": "ELEVENSES"}
    r = client.post("/food-logs", json=bad, headers=AUTH_HEADER)
    assert r.status_code == 422


def test_create_food_log_zero_grams_returns_422(client):
    _ensure_profile(client)
    bad = {**_VALID_LOG, "grams": 0}
    r = client.post("/food-logs", json=bad, headers=AUTH_HEADER)
    assert r.status_code == 422


def test_create_food_log_negative_kcal_returns_422(client):
    _ensure_profile(client)
    bad = {**_VALID_LOG, "kcal_100g": -1.0}
    r = client.post("/food-logs", json=bad, headers=AUTH_HEADER)
    assert r.status_code == 422


def test_delete_food_log_removes_row(client):
    _ensure_profile(client)
    created = client.post("/food-logs", json=_VALID_LOG, headers=AUTH_HEADER).json()
    log_id = created["id"]
    r = client.delete(f"/food-logs/{log_id}", headers=AUTH_HEADER)
    assert r.status_code == 204
    r2 = client.delete(f"/food-logs/{log_id}", headers=AUTH_HEADER)
    assert r2.status_code == 404


def test_delete_food_log_not_found_returns_404(client):
    _ensure_profile(client)
    r = client.delete("/food-logs/99999", headers=AUTH_HEADER)
    assert r.status_code == 404


def test_delete_food_log_of_another_user_returns_403(client, mock_firebase_token):
    _ensure_profile(client)
    created = client.post("/food-logs", json=_VALID_LOG, headers=AUTH_HEADER).json()
    log_id = created["id"]
    mock_firebase_token.return_value = {"uid": "different-uid", "email": "x@y.com"}
    client.post("/profile", json=_VALID_PROFILE, headers=AUTH_HEADER)
    r = client.delete(f"/food-logs/{log_id}", headers=AUTH_HEADER)
    assert r.status_code == 403


def test_patch_food_log_updates_grams(client):
    _ensure_profile(client)
    created = client.post("/food-logs", json=_VALID_LOG, headers=AUTH_HEADER).json()
    log_id = created["id"]
    r = client.patch(f"/food-logs/{log_id}", json={"grams": 175}, headers=AUTH_HEADER)
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["id"] == log_id
    assert body["grams"] == 175
    # snapshot-ul produsului rămâne neschimbat
    assert body["name"] == "Nutella"
    assert body["kcal_100g"] == 539.0


def test_foodlog_ingredient_and_update_shapes():
    from schemas import FoodLogIngredient, FoodLogUpdate, FoodLogCreate
    ing = FoodLogIngredient(name="Pui", grams=200, kcal_100g=165,
                            protein_100g=31, carbs_100g=0, fat_100g=3.6)
    assert ing.grams == 200
    upd = FoodLogUpdate(grams=300, ingredients=[ing])
    assert upd.ingredients[0].name == "Pui"
    create = FoodLogCreate(
        log_date="2026-06-04", meal="LUNCH", grams=450, barcode="recipe",
        name="X", kcal_100g=150, protein_100g=10, carbs_100g=20, fat_100g=5,
        ingredients=[ing],
    )
    assert create.ingredients[0].grams == 200


def test_create_and_patch_recipe_log_recomputes(client):
    _ensure_profile(client)
    body = {
        "log_date": "2026-06-04", "meal": "LUNCH", "grams": 350, "barcode": "recipe",
        "name": "Pilaf", "categories": ["recipe"],
        "kcal_100g": 150, "protein_100g": 10, "carbs_100g": 20, "fat_100g": 5,
        "ingredients": [
            {"name": "Pui", "grams": 200, "kcal_100g": 165, "protein_100g": 31, "carbs_100g": 0, "fat_100g": 3.6},
            {"name": "Orez", "grams": 150, "kcal_100g": 130, "protein_100g": 2.7, "carbs_100g": 28, "fat_100g": 0.3},
        ],
    }
    created = client.post("/food-logs", json=body, headers=AUTH_HEADER)
    assert created.status_code == 201, created.text
    lid = created.json()["id"]
    assert len(created.json()["ingredients"]) == 2

    patch_body = {"grams": 400, "ingredients": [
        {"name": "Pui", "grams": 250, "kcal_100g": 165, "protein_100g": 31, "carbs_100g": 0, "fat_100g": 3.6},
        {"name": "Orez", "grams": 150, "kcal_100g": 130, "protein_100g": 2.7, "carbs_100g": 28, "fat_100g": 0.3},
    ]}
    r = client.patch(f"/food-logs/{lid}", json=patch_body, headers=AUTH_HEADER)
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["grams"] == 400  # 250 + 150
    # total kcal = 165*2.5 + 130*1.5 = 607.5; per100g = 607.5/400*100
    assert abs(data["kcal_100g"] - (607.5 / 400 * 100)) < 0.5


def test_patch_food_log_persists(client):
    _ensure_profile(client)
    created = client.post("/food-logs", json=_VALID_LOG, headers=AUTH_HEADER).json()
    log_id = created["id"]
    client.patch(f"/food-logs/{log_id}", json={"grams": 200}, headers=AUTH_HEADER)
    day = client.get("/days/2026-05-20", headers=AUTH_HEADER).json()
    logged = day["foods_by_meal"]["breakfast"]
    assert any(f["id"] == log_id and f["grams"] == 200 for f in logged)


def test_patch_food_log_zero_grams_returns_422(client):
    _ensure_profile(client)
    created = client.post("/food-logs", json=_VALID_LOG, headers=AUTH_HEADER).json()
    log_id = created["id"]
    r = client.patch(f"/food-logs/{log_id}", json={"grams": 0}, headers=AUTH_HEADER)
    assert r.status_code == 422


def test_patch_food_log_not_found_returns_404(client):
    _ensure_profile(client)
    r = client.patch("/food-logs/99999", json={"grams": 150}, headers=AUTH_HEADER)
    assert r.status_code == 404


def test_patch_food_log_of_another_user_returns_403(client, mock_firebase_token):
    _ensure_profile(client)
    created = client.post("/food-logs", json=_VALID_LOG, headers=AUTH_HEADER).json()
    log_id = created["id"]
    mock_firebase_token.return_value = {"uid": "different-uid", "email": "x@y.com"}
    client.post("/profile", json=_VALID_PROFILE, headers=AUTH_HEADER)
    r = client.patch(f"/food-logs/{log_id}", json={"grams": 150}, headers=AUTH_HEADER)
    assert r.status_code == 403


def test_food_log_snapshot_persists_macros(client):
    _ensure_profile(client)
    created = client.post("/food-logs", json=_VALID_LOG, headers=AUTH_HEADER).json()
    assert created["kcal_100g"] == 539.0
    assert created["protein_100g"] == 6.3
    assert created["carbs_100g"] == 57.5
    assert created["fat_100g"] == 30.9
    assert created["categories"] == ["Spreads"]
    assert created["brand"] == "Ferrero"
    assert created["image_url"] == "https://example/nutella.jpg"


def test_create_with_unit_roundtrips(client):
    _ensure_profile(client)
    body = {**_VALID_LOG, "grams": 14, "unit": "tsp", "unit_qty": 2.0}
    r = client.post("/food-logs", json=body, headers=AUTH_HEADER)
    assert r.status_code == 201, r.text
    assert r.json()["unit"] == "tsp"
    assert r.json()["unit_qty"] == 2.0
    assert r.json()["grams"] == 14
    d = client.get(f"/days/{_VALID_LOG['log_date']}", headers=AUTH_HEADER)
    assert d.status_code == 200, d.text
    item = d.json()["foods_by_meal"]["breakfast"][0]
    assert item["unit"] == "tsp"
    assert item["unit_qty"] == 2.0
    assert item["grams"] == 14


def test_create_without_unit_defaults_null(client):
    _ensure_profile(client)
    r = client.post("/food-logs", json=_VALID_LOG, headers=AUTH_HEADER)
    assert r.status_code == 201, r.text
    assert r.json()["unit"] is None
    assert r.json()["unit_qty"] is None


def test_unit_without_qty_rejected(client):
    _ensure_profile(client)
    for extra in ({"unit": "tsp"}, {"unit_qty": 1.0}, {"unit": "tsp", "unit_qty": 0}):
        r = client.post("/food-logs", json={**_VALID_LOG, **extra}, headers=AUTH_HEADER)
        assert r.status_code == 422, (extra, r.text)


def test_unit_name_length_rejected(client):
    _ensure_profile(client)
    for unit in ("   ", "x" * 31):
        r = client.post(
            "/food-logs", json={**_VALID_LOG, "unit": unit, "unit_qty": 1.0},
            headers=AUTH_HEADER,
        )
        assert r.status_code == 422, (unit, r.text)


def test_patch_sets_and_clears_unit(client):
    _ensure_profile(client)
    lid = client.post("/food-logs", json=_VALID_LOG, headers=AUTH_HEADER).json()["id"]
    r = client.patch(
        f"/food-logs/{lid}",
        json={"grams": 30, "unit": "slice", "unit_qty": 1.0},
        headers=AUTH_HEADER,
    )
    assert r.status_code == 200, r.text
    assert r.json()["unit"] == "slice"
    assert r.json()["unit_qty"] == 1.0
    assert r.json()["grams"] == 30
    r = client.patch(f"/food-logs/{lid}", json={"grams": 50}, headers=AUTH_HEADER)
    assert r.status_code == 200, r.text
    assert r.json()["unit"] is None
    assert r.json()["unit_qty"] is None
    assert r.json()["grams"] == 50
