from tests.test_food_logs import AUTH_HEADER as H, _VALID_LOG, _ensure_profile

DAY = _VALID_LOG["log_date"]

_RECIPE_ING = [
    {"name": "Pui", "grams": 200, "kcal_100g": 165, "protein_100g": 31, "carbs_100g": 0, "fat_100g": 3.6},
    {"name": "Orez", "grams": 150, "kcal_100g": 130, "protein_100g": 2.7, "carbs_100g": 28, "fat_100g": 0.3},
]


def body(**kw):
    return {**_VALID_LOG, **kw}


def test_create_without_client_id_generates_one(client):
    _ensure_profile(client)
    r = client.post("/food-logs", json=body(), headers=H)
    assert r.status_code == 201, r.text
    assert len(r.json()["client_id"]) == 36


def test_create_same_client_id_is_idempotent(client):
    _ensure_profile(client)
    a = client.post("/food-logs", json=body(client_id="c-1"), headers=H)
    b = client.post("/food-logs", json=body(client_id="c-1", grams=999), headers=H)
    assert a.status_code == 201 and b.status_code == 200
    assert b.json()["id"] == a.json()["id"] and b.json()["grams"] == a.json()["grams"]
    assert len(client.get(f"/days/{DAY}", headers=H).json()["foods_by_meal"]["breakfast"]) == 1


def test_same_client_id_other_uid_is_separate_row(client, mock_firebase_token):
    _ensure_profile(client)
    a = client.post("/food-logs", json=body(client_id="c-1"), headers=H)
    mock_firebase_token.return_value = {"uid": "other-uid", "email": "o@test.com"}
    _ensure_profile(client)
    b = client.post("/food-logs", json=body(client_id="c-1"), headers=H)
    assert a.status_code == 201 and b.status_code == 201
    assert a.json()["id"] != b.json()["id"]


def test_patch_by_client_id_updates_grams_and_unit(client):
    _ensure_profile(client)
    client.post("/food-logs", json=body(client_id="c-1"), headers=H)
    r = client.patch(
        "/food-logs/by-client/c-1",
        json={"grams": 200, "unit": "felie", "unit_qty": 2}, headers=H,
    )
    assert r.status_code == 200, r.text
    assert r.json()["grams"] == 200 and r.json()["unit"] == "felie" and r.json()["unit_qty"] == 2


def test_patch_by_client_id_recipe_recomputes_totals(client):
    _ensure_profile(client)
    client.post("/food-logs", json=body(
        client_id="r-1", barcode="recipe", name="Pilaf", grams=350, ingredients=_RECIPE_ING,
    ), headers=H)
    ing = [{**_RECIPE_ING[0], "grams": 250}, _RECIPE_ING[1]]
    r = client.patch("/food-logs/by-client/r-1", json={"grams": 400, "ingredients": ing}, headers=H)
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["grams"] == 400
    assert abs(data["kcal_100g"] - (607.5 / 400 * 100)) < 0.5


def test_patch_by_client_id_unknown_is_404(client):
    _ensure_profile(client)
    r = client.patch("/food-logs/by-client/nope", json={"grams": 10}, headers=H)
    assert r.status_code == 404


def test_delete_by_client_id_then_again_is_404(client):
    _ensure_profile(client)
    client.post("/food-logs", json=body(client_id="c-1"), headers=H)
    assert client.delete("/food-logs/by-client/c-1", headers=H).status_code == 204
    assert client.delete("/food-logs/by-client/c-1", headers=H).status_code == 404


def test_copy_rows_get_distinct_client_ids(client):
    _ensure_profile(client)
    src = client.post("/food-logs", json=body(client_id="c-1"), headers=H).json()
    r = client.post("/food-logs/copy", json={
        "to_date": "2026-05-21",
        "items": [{"source_id": src["id"], "grams": 100}, {"source_id": src["id"], "grams": 120}],
    }, headers=H)
    assert r.status_code == 201, r.text
    ids = [x["client_id"] for x in r.json()]
    assert len(set(ids)) == 2 and all(len(i) == 36 for i in ids)
