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


def test_get_profile_returns_404_when_none(client):
    response = client.get("/profile", headers=AUTH_HEADER)
    assert response.status_code == 404


def test_save_profile_returns_200(client):
    response = client.post("/profile", json=_VALID_PROFILE, headers=AUTH_HEADER)
    assert response.status_code == 200
    assert response.json()["name"] == "Mihai"
    assert response.json()["uid"] == "test-uid-123"


def test_get_profile_after_save(client):
    client.post("/profile", json=_VALID_PROFILE, headers=AUTH_HEADER)
    response = client.get("/profile", headers=AUTH_HEADER)
    assert response.status_code == 200
    assert response.json()["activity_level"] == "MODERATE"


def test_save_profile_upsert_updates_existing(client):
    client.post("/profile", json=_VALID_PROFILE, headers=AUTH_HEADER)
    updated = {**_VALID_PROFILE, "current_weight_kg": 76.5}
    response = client.post("/profile", json=updated, headers=AUTH_HEADER)
    assert response.status_code == 200
    assert response.json()["current_weight_kg"] == 76.5


def test_profile_requires_auth(client):
    response = client.get("/profile")
    assert response.status_code == 422
