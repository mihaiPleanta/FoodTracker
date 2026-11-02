import pytest

from services.custom_foods import parse_custom_key, custom_key

AUTH_HEADER = {"Authorization": "Bearer fake-test-token"}

_VALID_PROFILE = {
    "name": "Mihai", "age": 22, "gender": "MALE", "height_cm": 180,
    "current_weight_kg": 78.0, "target_weight_kg": 75.0, "activity_level": "MODERATE",
}


def _ensure_profile(client):
    client.post("/profile", json=_VALID_PROFILE, headers=AUTH_HEADER)


@pytest.fixture(autouse=True)
def _profile(client):
    _ensure_profile(client)


def _body(**kw):
    b = {"name": "Biscuiți cu cacao", "brand": "Eugenia", "barcode": "5941234567890",
         "kcal_100g": 452, "protein_100g": 6.1, "carbs_100g": 68, "fat_100g": 17}
    b.update(kw)
    return b


def test_create_returns_key_and_lists(client):
    r = client.post("/custom-foods", json=_body(), headers=AUTH_HEADER)
    assert r.status_code == 201, r.text
    d = r.json()
    assert d["key"] == f"custom-{d['id']}"
    assert d["serving"] is None
    assert [f["name"] for f in client.get("/custom-foods", headers=AUTH_HEADER).json()] == ["Biscuiți cu cacao"]


def test_create_with_serving_creates_portion_unit(client):
    d = client.post("/custom-foods", json=_body(serving={"unit": "biscuit", "grams": 12}), headers=AUTH_HEADER).json()
    assert d["serving"] == {"unit": "biscuit", "grams": 12.0}
    units = client.get(f"/portions/{d['key']}", headers=AUTH_HEADER).json()["units"]
    assert {"unit": "biscuit", "grams": 12.0, "source": "user"} in units


@pytest.mark.parametrize("override", [
    {"kcal_100g": 901}, {"protein_100g": -1}, {"fat_100g": 101},
    {"protein_100g": 40, "carbs_100g": 40, "fat_100g": 30},
    {"name": "   "}, {"barcode": "12ab"}, {"barcode": "1234567"},
    {"serving": {"unit": "", "grams": 10}}, {"serving": {"unit": "felie", "grams": 0}},
])
def test_create_validation_422(client, override):
    assert client.post("/custom-foods", json=_body(**override), headers=AUTH_HEADER).status_code == 422


def test_blank_brand_and_barcode_become_null(client):
    d = client.post("/custom-foods", json=_body(brand="  ", barcode=""), headers=AUTH_HEADER).json()
    assert d["brand"] is None and d["barcode"] is None


def test_duplicate_barcode_same_user_409(client):
    first = client.post("/custom-foods", json=_body(), headers=AUTH_HEADER).json()
    r = client.post("/custom-foods", json=_body(name="Alt"), headers=AUTH_HEADER)
    assert r.status_code == 409
    assert r.json()["detail"] == {"code": "barcode_taken", "existing_id": first["id"]}


def test_same_barcode_other_user_allowed_and_isolated(client, mock_firebase_token):
    client.post("/custom-foods", json=_body(), headers=AUTH_HEADER)
    mock_firebase_token.return_value = {"uid": "other-uid", "email": "o@test.com"}
    _ensure_profile(client)
    assert client.get("/custom-foods", headers=AUTH_HEADER).json() == []
    assert client.post("/custom-foods", json=_body(), headers=AUTH_HEADER).status_code == 201


def test_list_alphabetical_ignoring_diacritics(client):
    for n in ["Zacuscă", "Ăluat", "Biscuiți"]:
        client.post("/custom-foods", json=_body(name=n, barcode=None), headers=AUTH_HEADER)
    assert [f["name"] for f in client.get("/custom-foods", headers=AUTH_HEADER).json()] == ["Ăluat", "Biscuiți", "Zacuscă"]


def test_parse_custom_key():
    assert parse_custom_key("custom-12") == 12
    assert parse_custom_key("custom-x") is None
    assert parse_custom_key("custom-") is None
    assert parse_custom_key("5941234567890") is None
    assert custom_key(7) == "custom-7"


def test_get_list_returns_serving(client):
    client.post("/custom-foods", json=_body(serving={"unit": "biscuit", "grams": 12}), headers=AUTH_HEADER)
    listed = client.get("/custom-foods", headers=AUTH_HEADER).json()
    assert listed[0]["serving"] == {"unit": "biscuit", "grams": 12.0}


def test_barcode_arabic_indic_digits_422(client):
    r = client.post("/custom-foods", json=_body(barcode="٥٩٤١٢٣٤٥٦٧٨٩٠"), headers=AUTH_HEADER)
    assert r.status_code == 422


def test_put_overwrites_and_keeps_key(client):
    d = client.post("/custom-foods", json=_body(), headers=AUTH_HEADER).json()
    r = client.put(f"/custom-foods/{d['id']}", json=_body(name="Nou", kcal_100g=400), headers=AUTH_HEADER)
    assert r.status_code == 200, r.text
    assert r.json()["key"] == d["key"] and r.json()["name"] == "Nou"
    listed = client.get("/custom-foods", headers=AUTH_HEADER).json()
    assert [(f["key"], f["name"], f["kcal_100g"]) for f in listed] == [(d["key"], "Nou", 400.0)]


def test_put_ignores_serving(client):
    d = client.post("/custom-foods", json=_body(serving={"unit": "biscuit", "grams": 12}), headers=AUTH_HEADER).json()
    r = client.put(f"/custom-foods/{d['id']}", json=_body(serving={"unit": "felie", "grams": 99}), headers=AUTH_HEADER)
    assert r.status_code == 200
    assert r.json()["serving"] == {"unit": "biscuit", "grams": 12.0}


def test_put_barcode_conflict_409(client):
    a = client.post("/custom-foods", json=_body(barcode="11111111"), headers=AUTH_HEADER).json()
    b = client.post("/custom-foods", json=_body(name="B", barcode="22222222"), headers=AUTH_HEADER).json()
    r = client.put(f"/custom-foods/{b['id']}", json=_body(name="B", barcode="11111111"), headers=AUTH_HEADER)
    assert r.status_code == 409
    assert r.json()["detail"] == {"code": "barcode_taken", "existing_id": a["id"]}
    assert client.put(f"/custom-foods/{a['id']}", json=_body(barcode="11111111"), headers=AUTH_HEADER).status_code == 200


def test_barcode_race_integrity_error_maps_to_409(client, monkeypatch):
    import routers.custom_foods as cf
    first = client.post("/custom-foods", json=_body(), headers=AUTH_HEADER).json()
    real = cf.find_by_barcode
    calls = {"n": 0}

    async def flaky(db, uid, barcode):
        calls["n"] += 1
        return None if calls["n"] == 1 else await real(db, uid, barcode)

    monkeypatch.setattr(cf, "find_by_barcode", flaky)
    r = client.post("/custom-foods", json=_body(name="Alt"), headers=AUTH_HEADER)
    assert r.status_code == 409, r.text
    assert r.json()["detail"] == {"code": "barcode_taken", "existing_id": first["id"]}
    assert len(client.get("/custom-foods", headers=AUTH_HEADER).json()) == 1


def test_put_and_delete_other_user_404(client, mock_firebase_token):
    d = client.post("/custom-foods", json=_body(), headers=AUTH_HEADER).json()
    mock_firebase_token.return_value = {"uid": "other-uid", "email": "o@test.com"}
    _ensure_profile(client)
    assert client.put(f"/custom-foods/{d['id']}", json=_body(), headers=AUTH_HEADER).status_code == 404
    assert client.delete(f"/custom-foods/{d['id']}", headers=AUTH_HEADER).status_code == 404
    assert client.delete("/custom-foods/99999", headers=AUTH_HEADER).status_code == 404


def test_delete_removes_units_presets_favorite_keeps_logs(client):
    d = client.post("/custom-foods", json=_body(serving={"unit": "biscuit", "grams": 12}), headers=AUTH_HEADER).json()
    key = d["key"]
    assert client.put(f"/portions/{key}/presets", json={"grams": [30]}, headers=AUTH_HEADER).status_code == 200
    fav = {"name": d["name"], "kcal_100g": 452, "protein_100g": 6.1, "carbs_100g": 68, "fat_100g": 17}
    assert client.put(f"/favorites/{key}", json=fav, headers=AUTH_HEADER).status_code == 200
    log = {"log_date": "2026-05-20", "meal": "BREAKFAST", "grams": 50, "barcode": key,
           "name": d["name"], "categories": [], "kcal_100g": 452, "protein_100g": 6.1,
           "carbs_100g": 68, "fat_100g": 17}
    assert client.post("/food-logs", json=log, headers=AUTH_HEADER).status_code == 201

    assert client.delete(f"/custom-foods/{d['id']}", headers=AUTH_HEADER).status_code == 204

    assert client.get("/custom-foods", headers=AUTH_HEADER).json() == []
    assert client.get("/favorites", headers=AUTH_HEADER).json() == []
    portions = client.get(f"/portions/{key}", headers=AUTH_HEADER).json()
    assert all(u["source"] != "user" for u in portions["units"])
    assert portions["presets_custom"] is False
    day = client.get("/days/2026-05-20", headers=AUTH_HEADER).json()
    items = day["foods_by_meal"]["breakfast"]
    assert len(items) == 1 and items[0]["kcal_100g"] == 452 and items[0]["grams"] == 50


# ── recente / favorite folosesc valorile curente ─────────────────────────────

def _mk(client):
    return client.post("/custom-foods", json=_body(), headers=AUTH_HEADER).json()


def _log(client, key, grams=50, kcal=452, name="Biscuiți cu cacao"):
    log = {"log_date": "2026-05-20", "meal": "BREAKFAST", "grams": grams, "barcode": key,
           "name": name, "categories": [], "kcal_100g": kcal, "protein_100g": 6.1,
           "carbs_100g": 68, "fat_100g": 17}
    assert client.post("/food-logs", json=log, headers=AUTH_HEADER).status_code == 201


def test_recent_uses_current_custom_values(client):
    d = _mk(client)
    _log(client, d["key"], grams=50)
    r = client.put(f"/custom-foods/{d['id']}", json=_body(name="Nou", kcal_100g=400), headers=AUTH_HEADER)
    assert r.status_code == 200
    items = client.get("/foods/recent?meal=BREAKFAST", headers=AUTH_HEADER).json()
    assert len(items) == 1
    assert items[0]["food"]["barcode"] == d["key"]
    assert items[0]["food"]["kcal_100g"] == 400
    assert items[0]["food"]["name"] == "Nou"
    assert items[0]["last_grams"] == 50


def test_recent_drops_deleted_custom(client):
    d = _mk(client)
    _log(client, d["key"])
    assert client.delete(f"/custom-foods/{d['id']}", headers=AUTH_HEADER).status_code == 204
    items = client.get("/foods/recent?meal=BREAKFAST", headers=AUTH_HEADER).json()
    assert all(i["food"]["barcode"] != d["key"] for i in items)
    assert items == []


def test_favorites_use_current_custom_values(client):
    d = _mk(client)
    fav = {"name": d["name"], "kcal_100g": 452, "protein_100g": 6.1, "carbs_100g": 68, "fat_100g": 17}
    assert client.put(f"/favorites/{d['key']}", json=fav, headers=AUTH_HEADER).status_code == 200
    client.put(f"/custom-foods/{d['id']}", json=_body(name="Nou", kcal_100g=400), headers=AUTH_HEADER)
    items = client.get("/favorites", headers=AUTH_HEADER).json()
    assert len(items) == 1
    assert items[0]["kcal_100g"] == 400 and items[0]["name"] == "Nou"


def test_recent_non_custom_untouched(client):
    _log(client, "3017620422003", kcal=539, name="Nutella")
    items = client.get("/foods/recent?meal=BREAKFAST", headers=AUTH_HEADER).json()
    assert len(items) == 1
    assert items[0]["food"]["kcal_100g"] == 539 and items[0]["food"]["name"] == "Nutella"
