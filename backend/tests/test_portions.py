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
