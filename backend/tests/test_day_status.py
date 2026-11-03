import os
import tempfile
from datetime import date, datetime, timedelta, timezone

from sqlalchemy import create_engine, text

from services.nutrition import calculate_goals

AUTH_HEADER = {"Authorization": "Bearer fake-test-token"}

_PROFILE = {
    "name": "Mihai",
    "age": 22,
    "gender": "MALE",
    "height_cm": 180,
    "current_weight_kg": 78.0,
    "target_weight_kg": 75.0,
    "activity_level": "MODERATE",
}

DAY = "2026-05-20"


class _P:
    def __init__(self, **kw):
        self.__dict__.update(kw)


def _ensure_profile(client, **overrides):
    r = client.post("/profile", json={**_PROFILE, **overrides}, headers=AUTH_HEADER)
    assert r.status_code == 200


def _row(day=DAY, uid="test-uid-123"):
    engine = create_engine(
        "sqlite:///" + os.path.join(tempfile.gettempdir(), "foodtracker_test.db")
    )
    with engine.connect() as conn:
        return conn.execute(
            text(
                "SELECT complete, calorie_goal, protein_goal_g, carbs_goal_g, "
                "fat_goal_g, tdee FROM day_status WHERE uid = :u AND log_date = :d"
            ),
            {"u": uid, "d": day},
        ).fetchone()


def _put(client, complete, day=DAY):
    return client.put(
        "/days/%s/status" % day, json={"complete": complete}, headers=AUTH_HEADER
    )


def test_get_day_status_null_by_default(client):
    _ensure_profile(client)
    r = client.get("/days/" + DAY, headers=AUTH_HEADER)
    assert r.status_code == 200
    assert r.json()["status"] is None


def test_put_complete_sets_status_and_snapshot(client):
    _ensure_profile(client)
    r = _put(client, True)
    assert r.status_code == 200
    assert r.json() == {"date": DAY, "status": "complete"}
    assert client.get("/days/" + DAY, headers=AUTH_HEADER).json()["status"] == "complete"

    goals = calculate_goals(_P(**_PROFILE))
    row = _row()
    assert row.complete in (True, 1)
    assert row.calorie_goal == goals.calorie_goal
    assert row.protein_goal_g == goals.protein_goal_g
    assert row.carbs_goal_g == goals.carbs_goal_g
    assert row.fat_goal_g == goals.fat_goal_g
    assert row.tdee == goals.tdee


def test_snapshot_survives_goal_change(client):
    _ensure_profile(client)
    deficit_goal = calculate_goals(_P(**_PROFILE)).calorie_goal
    _put(client, True)
    _ensure_profile(client, target_weight_kg=_PROFILE["current_weight_kg"])
    maintenance_goal = calculate_goals(
        _P(**{**_PROFILE, "target_weight_kg": _PROFILE["current_weight_kg"]})
    ).calorie_goal
    assert maintenance_goal != deficit_goal
    assert _row().calorie_goal == deficit_goal


def test_reput_complete_overwrites_snapshot(client):
    _ensure_profile(client)
    _put(client, True)
    _ensure_profile(client, target_weight_kg=_PROFILE["current_weight_kg"])
    _put(client, True)
    maintenance_goal = calculate_goals(
        _P(**{**_PROFILE, "target_weight_kg": _PROFILE["current_weight_kg"]})
    ).calorie_goal
    assert _row().calorie_goal == maintenance_goal


def test_put_incomplete_has_no_snapshot(client):
    _ensure_profile(client)
    r = _put(client, False)
    assert r.status_code == 200
    assert r.json() == {"date": DAY, "status": "incomplete"}
    row = _row()
    assert row.complete in (False, 0)
    assert row.calorie_goal is None
    assert row.protein_goal_g is None
    assert row.carbs_goal_g is None
    assert row.fat_goal_g is None
    assert row.tdee is None


def test_put_incomplete_after_complete_clears_snapshot(client):
    _ensure_profile(client)
    _put(client, True)
    _put(client, False)
    assert _row().calorie_goal is None


def test_delete_resets_to_null(client):
    _ensure_profile(client)
    _put(client, True)
    r = client.delete("/days/%s/status" % DAY, headers=AUTH_HEADER)
    assert r.status_code == 204
    assert client.get("/days/" + DAY, headers=AUTH_HEADER).json()["status"] is None
    assert _row() is None


def test_delete_is_idempotent(client):
    _ensure_profile(client)
    assert client.delete("/days/%s/status" % DAY, headers=AUTH_HEADER).status_code == 204
    assert client.delete("/days/%s/status" % DAY, headers=AUTH_HEADER).status_code == 204


def test_put_accepts_utc_tomorrow(client):
    _ensure_profile(client)
    tomorrow = datetime.now(timezone.utc).date() + timedelta(days=1)
    assert _put(client, True, tomorrow.isoformat()).status_code == 200


def test_put_rejects_two_days_ahead(client):
    _ensure_profile(client)
    too_far = datetime.now(timezone.utc).date() + timedelta(days=2)
    assert _put(client, True, too_far.isoformat()).status_code == 422


def test_put_without_profile_returns_404(client):
    assert _put(client, True).status_code == 404


def test_status_is_per_user(client, mock_firebase_token):
    _ensure_profile(client)
    _put(client, True)
    mock_firebase_token.return_value = {"uid": "other-uid", "email": "o@test.com"}
    r = client.get("/days/" + DAY, headers=AUTH_HEADER)
    assert r.json()["status"] is None
