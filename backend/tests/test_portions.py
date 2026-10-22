import os
import tempfile

from sqlalchemy import create_engine, select
from sqlalchemy.orm import Session

from models import UserGramPresets, UserPortionUnit

_sync_engine = create_engine(
    "sqlite:///" + os.path.join(tempfile.gettempdir(), "foodtracker_test.db")
)

AUTH_HEADER = {"Authorization": "Bearer fake-test-token"}

_VALID_PROFILE = {
    "name": "Mihai", "age": 22, "gender": "MALE", "height_cm": 180,
    "current_weight_kg": 78.0, "target_weight_kg": 75.0, "activity_level": "MODERATE",
}

BC = "3017620422003"


def _ensure_profile(client):
    client.post("/profile", json=_VALID_PROFILE, headers=AUTH_HEADER)


def _units():
    with Session(_sync_engine) as s:
        return list(s.execute(select(UserPortionUnit)).scalars())


def _presets():
    with Session(_sync_engine) as s:
        return list(s.execute(select(UserGramPresets)).scalars())


def _put_unit(client, unit, grams, barcode=BC):
    return client.put(
        f"/portions/{barcode}/units", json={"unit": unit, "grams": grams}, headers=AUTH_HEADER
    )


def test_put_custom_unit_and_upsert_case_insensitive(client):
    _ensure_profile(client)
    assert _put_unit(client, "Bolul meu", 250).status_code == 200
    r = _put_unit(client, " bolul MEU ", 300)
    assert r.status_code == 200
    assert r.json() == {"unit": "bolul MEU", "grams": 300}
    rows = _units()
    assert len(rows) == 1
    assert rows[0].grams == 300
    assert rows[0].unit == "bolul MEU"


def test_put_standard_correction(client):
    _ensure_profile(client)
    r = _put_unit(client, "tsp", 6)
    assert r.status_code == 200
    assert r.json() == {"unit": "tsp", "grams": 6}


def test_reject_custom_named_like_standard_key(client):
    _ensure_profile(client)
    assert _put_unit(client, " TSP ", 5).status_code == 422


def test_reject_bad_names_and_grams(client):
    _ensure_profile(client)
    assert _put_unit(client, "", 5).status_code == 422
    assert _put_unit(client, "   ", 5).status_code == 422
    assert _put_unit(client, "x" * 31, 5).status_code == 422
    assert _put_unit(client, "Bol", 0).status_code == 422
    assert _put_unit(client, "Bol", 5001).status_code == 422
    assert _units() == []


def test_delete_unit_idempotent(client):
    _ensure_profile(client)
    _put_unit(client, "Bolul meu", 250)
    url = f"/portions/{BC}/units?unit= BOLUL meu"
    assert client.delete(url, headers=AUTH_HEADER).status_code == 204
    assert _units() == []
    assert client.delete(url, headers=AUTH_HEADER).status_code == 204


def test_presets_put_and_reset(client):
    _ensure_profile(client)
    url = f"/portions/{BC}/presets"
    r = client.put(url, json={"grams": [30, 60]}, headers=AUTH_HEADER)
    assert r.status_code == 200
    assert r.json() == {"grams": [30, 60]}
    client.put(url, json={"grams": [40]}, headers=AUTH_HEADER)
    assert [p.presets for p in _presets()] == [[40]]
    assert client.put(url, json={"grams": [1, 2, 3, 4, 5, 6]}, headers=AUTH_HEADER).status_code == 422
    assert client.put(url, json={"grams": [0]}, headers=AUTH_HEADER).status_code == 422
    assert client.put(url, json={"grams": [5001]}, headers=AUTH_HEADER).status_code == 422
    assert client.put(url, json={"grams": []}, headers=AUTH_HEADER).status_code == 422
    assert client.delete(url, headers=AUTH_HEADER).status_code == 204
    assert _presets() == []


def test_isolation_between_users(client, mock_firebase_token):
    _ensure_profile(client)
    _put_unit(client, "Bolul meu", 250)
    client.put(f"/portions/{BC}/presets", json={"grams": [30]}, headers=AUTH_HEADER)
    mock_firebase_token.return_value = {"uid": "other-uid", "email": "o@test.com"}
    _ensure_profile(client)
    client.delete(f"/portions/{BC}/units?unit=Bolul meu", headers=AUTH_HEADER)
    client.delete(f"/portions/{BC}/presets", headers=AUTH_HEADER)
    assert [(u.uid, u.grams) for u in _units()] == [("test-uid-123", 250)]
    assert [p.uid for p in _presets()] == ["test-uid-123"]
    # same name for another user is a separate row
    _put_unit(client, "Bolul meu", 111)
    assert len(_units()) == 2


def test_uid_fk_cascades_on_profile_delete():
    # SQLite in tests doesn't enforce FKs, so assert the schema declares the cascade.
    for model in (UserPortionUnit, UserGramPresets):
        fk = next(iter(model.__table__.c.uid.foreign_keys))
        assert fk.column.table.name == "profiles"
        assert fk.ondelete == "CASCADE"


# ---------- GET /portions/{barcode} ----------

from datetime import date

import httpx
import pytest

from main import app
from services.generic_foods import GenericFoodsClient, get_generic_client
from services.open_food_facts import get_off_client
from services.portions import ProductPortionInfo


class _FakeOff:
    def __init__(self):
        self.info = None
        self.error = None
        self.calls = []

    async def get_portion_info(self, barcode):
        self.calls.append(barcode)
        if self.error is not None:
            raise self.error
        return self.info


class _FakeGeneric:
    def __init__(self):
        self._real = GenericFoodsClient()

    def get(self, food_id):
        return self._real.get(food_id)


@pytest.fixture
def fake_off():
    fake = _FakeOff()
    app.dependency_overrides[get_off_client] = lambda: fake
    app.dependency_overrides[get_generic_client] = lambda: _FakeGeneric()
    yield fake
    app.dependency_overrides.pop(get_off_client, None)
    app.dependency_overrides.pop(get_generic_client, None)


def _get_portions(client, barcode=BC):
    r = client.get(f"/portions/{barcode}", headers=AUTH_HEADER)
    assert r.status_code == 200, r.text
    return r.json()


def _log(client, grams, barcode=BC, **extra):
    body = {
        "log_date": date.today().isoformat(), "meal": "LUNCH", "grams": grams,
        "barcode": barcode, "name": "Miere", "kcal_100g": 300, "protein_100g": 0.5,
        "carbs_100g": 80, "fat_100g": 0, **extra,
    }
    r = client.post("/food-logs", json=body, headers=AUTH_HEADER)
    assert r.status_code == 201, r.text


def test_portions_off_label_and_estimate(client, fake_off):
    _ensure_profile(client)
    fake_off.info = ProductPortionInfo(["spreads", "honeys"], None, 900)
    body = _get_portions(client)
    assert body["units"] == [
        {"unit": "package", "grams": 900.0, "source": "label"},
        {"unit": "tsp", "grams": 7.0, "source": "estimate"},
        {"unit": "tbsp", "grams": 21.0, "source": "estimate"},
    ]
    assert body["presets_custom"] is False
    assert body["last"] is None


def test_portions_user_correction_and_custom(client, fake_off):
    _ensure_profile(client)
    fake_off.info = ProductPortionInfo(["spreads", "honeys"], None, 900)
    _put_unit(client, "tsp", 6)
    _put_unit(client, "Bolul meu", 250)
    units = _get_portions(client)["units"]
    assert [u["unit"] for u in units] == ["package", "tsp", "tbsp", "Bolul meu"]
    assert units[1] == {"unit": "tsp", "grams": 6.0, "source": "user"}
    assert units[-1] == {"unit": "Bolul meu", "grams": 250.0, "source": "user"}


def test_portions_history_presets_and_last(client, fake_off):
    _ensure_profile(client)
    fake_off.info = ProductPortionInfo(["spreads", "honeys"], None, 900)
    _log(client, 180)
    _log(client, 180)
    _log(client, 14, unit="tsp", unit_qty=2)
    _log(client, 500, ingredients=[{
        "name": "x", "grams": 500, "kcal_100g": 1, "protein_100g": 1,
        "carbs_100g": 1, "fat_100g": 1,
    }])
    body = _get_portions(client)
    assert body["gram_presets"][0] == 180
    assert body["last"] == {"unit": "tsp", "unit_qty": 2.0, "grams": 14}


def test_portions_custom_presets(client, fake_off):
    _ensure_profile(client)
    fake_off.info = None
    client.put(f"/portions/{BC}/presets", json={"grams": [42]}, headers=AUTH_HEADER)
    body = _get_portions(client)
    assert body["gram_presets"] == [42]
    assert body["presets_custom"] is True


def test_portions_off_down_still_200(client, fake_off):
    _ensure_profile(client)
    fake_off.error = httpx.ConnectError("down")
    _put_unit(client, "Bolul meu", 250)
    body = _get_portions(client)
    assert body["units"] == [{"unit": "Bolul meu", "grams": 250.0, "source": "user"}]
    assert body["gram_presets"] == [50, 100, 150, 200, 250]


def test_portions_generic_id(client, fake_off):
    _ensure_profile(client)
    body = _get_portions(client, "usda-egg-whole-raw")
    piece = [u for u in body["units"] if u["unit"] == "piece_large"]
    assert piece and piece[0]["grams"] == 50.0
    assert fake_off.calls == []


def test_portions_unknown_id(client, fake_off):
    _ensure_profile(client)
    body = _get_portions(client, "recipe-xyz")
    assert body["units"] == []
    assert body["gram_presets"] == [50, 100, 150, 200, 250]
    assert fake_off.calls == []
