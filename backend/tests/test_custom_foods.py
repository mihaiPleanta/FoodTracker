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
