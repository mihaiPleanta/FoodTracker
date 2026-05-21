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


def test_post_weight_check_in_creates_row_and_updates_profile(client):
    _ensure_profile(client)
    r = client.post(
        "/weight-check-ins",
        json={"date": "2026-05-20", "weight_kg": 77.5},
        headers=AUTH_HEADER,
    )
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["date"] == "2026-05-20"
    assert body["weight_kg"] == 77.5
    profile = client.get("/profile", headers=AUTH_HEADER).json()
    assert profile["current_weight_kg"] == 77.5


def test_post_weight_check_in_upsert_replaces_same_day(client):
    _ensure_profile(client)
    client.post(
        "/weight-check-ins",
        json={"date": "2026-05-20", "weight_kg": 77.5},
        headers=AUTH_HEADER,
    )
    r = client.post(
        "/weight-check-ins",
        json={"date": "2026-05-20", "weight_kg": 77.0},
        headers=AUTH_HEADER,
    )
    assert r.status_code == 200
    assert r.json()["weight_kg"] == 77.0


def test_older_check_in_does_not_update_profile(client):
    _ensure_profile(client)
    # Most recent — updates profile
    client.post(
        "/weight-check-ins",
        json={"date": "2026-05-20", "weight_kg": 77.5},
        headers=AUTH_HEADER,
    )
    # Older — must NOT change profile
    r = client.post(
        "/weight-check-ins",
        json={"date": "2026-05-10", "weight_kg": 80.0},
        headers=AUTH_HEADER,
    )
    assert r.status_code == 200
    profile = client.get("/profile", headers=AUTH_HEADER).json()
    assert profile["current_weight_kg"] == 77.5


def test_get_weight_check_ins_range_returns_sorted_asc(client):
    _ensure_profile(client)
    client.post("/weight-check-ins", json={"date": "2026-05-15", "weight_kg": 78.0}, headers=AUTH_HEADER)
    client.post("/weight-check-ins", json={"date": "2026-05-12", "weight_kg": 78.5}, headers=AUTH_HEADER)
    client.post("/weight-check-ins", json={"date": "2026-05-20", "weight_kg": 77.5}, headers=AUTH_HEADER)
    r = client.get("/weight-check-ins?from=2026-05-10&to=2026-05-20", headers=AUTH_HEADER)
    assert r.status_code == 200
    items = r.json()["items"]
    assert [it["date"] for it in items] == ["2026-05-12", "2026-05-15", "2026-05-20"]


def test_get_weight_check_ins_bad_range_returns_422(client):
    _ensure_profile(client)
    r = client.get("/weight-check-ins?from=2026-05-20&to=2026-05-10", headers=AUTH_HEADER)
    assert r.status_code == 422


def test_get_weight_check_ins_too_wide_range_returns_422(client):
    _ensure_profile(client)
    r = client.get("/weight-check-ins?from=2024-01-01&to=2026-05-20", headers=AUTH_HEADER)
    assert r.status_code == 422
