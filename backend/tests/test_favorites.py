from models import FavoriteFood

AUTH_HEADER = {"Authorization": "Bearer fake-test-token"}

_VALID_PROFILE = {
    "name": "Mihai", "age": 22, "gender": "MALE", "height_cm": 180,
    "current_weight_kg": 78.0, "target_weight_kg": 75.0, "activity_level": "MODERATE",
}


def _food(barcode="3017620422003", name="Nutella", kcal=539.0):
    return {
        "barcode": barcode, "name": name, "brand": "Ferrero",
        "image_url": "https://example/n.jpg", "categories": ["Spreads"],
        "kcal_100g": kcal, "protein_100g": 6.3, "carbs_100g": 57.5, "fat_100g": 30.9,
    }


def _ensure_profile(client):
    client.post("/profile", json=_VALID_PROFILE, headers=AUTH_HEADER)


def test_get_favorites_empty(client):
    _ensure_profile(client)
    r = client.get("/favorites", headers=AUTH_HEADER)
    assert r.status_code == 200
    assert r.json() == []


def test_put_creates_favorite(client):
    _ensure_profile(client)
    r = client.put("/favorites/3017620422003", json=_food(), headers=AUTH_HEADER)
    assert r.status_code == 200, r.text
    assert r.json()["name"] == "Nutella"
    listed = client.get("/favorites", headers=AUTH_HEADER).json()
    assert [f["barcode"] for f in listed] == ["3017620422003"]


def test_put_twice_updates_without_duplicate(client):
    _ensure_profile(client)
    client.put("/favorites/3017620422003", json=_food(), headers=AUTH_HEADER)
    client.put("/favorites/3017620422003", json=_food(name="Nutella 2", kcal=540.0), headers=AUTH_HEADER)
    listed = client.get("/favorites", headers=AUTH_HEADER).json()
    assert len(listed) == 1
    assert listed[0]["name"] == "Nutella 2"
    assert listed[0]["kcal_100g"] == 540.0


def test_path_barcode_wins_over_body(client):
    _ensure_profile(client)
    client.put("/favorites/111", json=_food(barcode="999"), headers=AUTH_HEADER)
    listed = client.get("/favorites", headers=AUTH_HEADER).json()
    assert listed[0]["barcode"] == "111"


def test_list_newest_first(client):
    _ensure_profile(client)
    client.put("/favorites/a1", json=_food(barcode="a1", name="A"), headers=AUTH_HEADER)
    client.put("/favorites/b2", json=_food(barcode="b2", name="B"), headers=AUTH_HEADER)
    listed = client.get("/favorites", headers=AUTH_HEADER).json()
    assert [f["barcode"] for f in listed] == ["b2", "a1"]


def test_delete_removes_and_is_idempotent(client):
    _ensure_profile(client)
    client.put("/favorites/3017620422003", json=_food(), headers=AUTH_HEADER)
    assert client.delete("/favorites/3017620422003", headers=AUTH_HEADER).status_code == 204
    assert client.delete("/favorites/3017620422003", headers=AUTH_HEADER).status_code == 204
    assert client.get("/favorites", headers=AUTH_HEADER).json() == []


def test_put_and_delete_generic_id(client):
    _ensure_profile(client)
    gid = "usda-chicken-breast-raw"
    r = client.put(f"/favorites/{gid}", json=_food(barcode=gid, name="Piept de pui crud"), headers=AUTH_HEADER)
    assert r.status_code == 200
    assert client.get("/favorites", headers=AUTH_HEADER).json()[0]["barcode"] == gid
    assert client.delete(f"/favorites/{gid}", headers=AUTH_HEADER).status_code == 204


def test_negative_macros_rejected(client):
    _ensure_profile(client)
    r = client.put("/favorites/x", json=_food(kcal=-1.0), headers=AUTH_HEADER)
    assert r.status_code == 422


def test_favorites_isolated_between_users(client, mock_firebase_token):
    _ensure_profile(client)
    client.put("/favorites/3017620422003", json=_food(), headers=AUTH_HEADER)
    mock_firebase_token.return_value = {"uid": "other-uid", "email": "o@test.com"}
    _ensure_profile(client)
    assert client.get("/favorites", headers=AUTH_HEADER).json() == []
    # other user's delete must not touch the first user's row
    client.delete("/favorites/3017620422003", headers=AUTH_HEADER)
    mock_firebase_token.return_value = {"uid": "test-uid-123", "email": "test@test.com"}
    assert len(client.get("/favorites", headers=AUTH_HEADER).json()) == 1


def test_uid_fk_cascades_on_profile_delete():
    # SQLite in tests doesn't enforce FKs, so assert the schema declares the cascade.
    fk = next(iter(FavoriteFood.__table__.c.uid.foreign_keys))
    assert fk.column.table.name == "profiles"
    assert fk.ondelete == "CASCADE"
