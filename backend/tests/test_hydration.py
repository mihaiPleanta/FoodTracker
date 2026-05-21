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


def _ensure_profile(client):
    client.post("/profile", json=_VALID_PROFILE, headers=AUTH_HEADER)


def test_put_hydration_creates_row(client):
    _ensure_profile(client)
    r = client.put("/hydration/2026-05-20", json={"liters": 0.75}, headers=AUTH_HEADER)
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["date"] == "2026-05-20"
    assert body["liters"] == 0.75


def test_put_hydration_updates_existing(client):
    _ensure_profile(client)
    client.put("/hydration/2026-05-20", json={"liters": 0.75}, headers=AUTH_HEADER)
    r = client.put("/hydration/2026-05-20", json={"liters": 1.5}, headers=AUTH_HEADER)
    assert r.status_code == 200
    assert r.json()["liters"] == 1.5


def test_put_hydration_negative_returns_422(client):
    _ensure_profile(client)
    r = client.put("/hydration/2026-05-20", json={"liters": -0.1}, headers=AUTH_HEADER)
    assert r.status_code == 422


def test_put_hydration_too_high_returns_422(client):
    _ensure_profile(client)
    r = client.put("/hydration/2026-05-20", json={"liters": 10.1}, headers=AUTH_HEADER)
    assert r.status_code == 422
