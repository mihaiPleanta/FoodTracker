from auth import get_user_deleter
from main import app

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


def test_delete_profile_requires_auth(client):
    response = client.delete("/profile")
    assert response.status_code == 422


def test_delete_profile_returns_204_and_removes_row(client):
    deleted_uids = []
    app.dependency_overrides[get_user_deleter] = lambda: deleted_uids.append
    try:
        client.post("/profile", json=_VALID_PROFILE, headers=AUTH_HEADER)
        response = client.delete("/profile", headers=AUTH_HEADER)
        assert response.status_code == 204
        assert deleted_uids == ["test-uid-123"]
        assert client.get("/profile", headers=AUTH_HEADER).status_code == 404
    finally:
        app.dependency_overrides.pop(get_user_deleter, None)


def test_delete_profile_without_profile_still_204(client):
    deleted_uids = []
    app.dependency_overrides[get_user_deleter] = lambda: deleted_uids.append
    try:
        response = client.delete("/profile", headers=AUTH_HEADER)
        assert response.status_code == 204
        assert deleted_uids == ["test-uid-123"]
    finally:
        app.dependency_overrides.pop(get_user_deleter, None)


def test_delete_profile_returns_502_when_firebase_fails(client):
    def raising_deleter():
        def _delete(uid: str) -> None:
            raise RuntimeError("firebase down")
        return _delete
    app.dependency_overrides[get_user_deleter] = raising_deleter
    try:
        client.post("/profile", json=_VALID_PROFILE, headers=AUTH_HEADER)
        response = client.delete("/profile", headers=AUTH_HEADER)
        assert response.status_code == 502
        assert client.get("/profile", headers=AUTH_HEADER).status_code == 200
    finally:
        app.dependency_overrides.pop(get_user_deleter, None)
