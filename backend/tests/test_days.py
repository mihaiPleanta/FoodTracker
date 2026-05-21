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
    "meal": "LUNCH",
    "grams": 200,
    "barcode": "111",
    "name": "Yogurt",
    "categories": [],
    "kcal_100g": 60.0,
    "protein_100g": 4.0,
    "carbs_100g": 5.0,
    "fat_100g": 2.0,
}


def _ensure_profile(client):
    client.post("/profile", json=_VALID_PROFILE, headers=AUTH_HEADER)


def test_get_day_empty_returns_zeros(client):
    _ensure_profile(client)
    r = client.get("/days/2026-05-20", headers=AUTH_HEADER)
    assert r.status_code == 200
    body = r.json()
    assert body["foods_by_meal"] == {"breakfast": [], "lunch": [], "dinner": [], "snacks": []}
    assert body["hydration_liters"] == 0.0
    assert body["weight_check_in"] is None


def test_get_day_returns_food_logs_grouped_by_meal(client):
    _ensure_profile(client)
    client.post("/food-logs", json=_VALID_LOG, headers=AUTH_HEADER)
    breakfast = {**_VALID_LOG, "meal": "BREAKFAST", "name": "Toast"}
    client.post("/food-logs", json=breakfast, headers=AUTH_HEADER)
    r = client.get("/days/2026-05-20", headers=AUTH_HEADER)
    assert r.status_code == 200
    body = r.json()
    assert len(body["foods_by_meal"]["lunch"]) == 1
    assert body["foods_by_meal"]["lunch"][0]["name"] == "Yogurt"
    assert len(body["foods_by_meal"]["breakfast"]) == 1
    assert body["foods_by_meal"]["breakfast"][0]["name"] == "Toast"
    assert len(body["foods_by_meal"]["dinner"]) == 0
    assert len(body["foods_by_meal"]["snacks"]) == 0


def test_get_day_returns_hydration_and_weight(client):
    _ensure_profile(client)
    client.put("/hydration/2026-05-20", json={"liters": 1.25}, headers=AUTH_HEADER)
    client.post(
        "/weight-check-ins",
        json={"date": "2026-05-20", "weight_kg": 77.0},
        headers=AUTH_HEADER,
    )
    r = client.get("/days/2026-05-20", headers=AUTH_HEADER)
    body = r.json()
    assert body["hydration_liters"] == 1.25
    assert body["weight_check_in"]["weight_kg"] == 77.0


def test_get_day_does_not_bleed_across_days(client):
    _ensure_profile(client)
    client.post("/food-logs", json=_VALID_LOG, headers=AUTH_HEADER)  # 2026-05-20
    r = client.get("/days/2026-05-21", headers=AUTH_HEADER)
    assert r.status_code == 200
    body = r.json()
    assert all(len(v) == 0 for v in body["foods_by_meal"].values())


def test_get_day_invalid_date_format_returns_422(client):
    _ensure_profile(client)
    r = client.get("/days/not-a-date", headers=AUTH_HEADER)
    assert r.status_code == 422
