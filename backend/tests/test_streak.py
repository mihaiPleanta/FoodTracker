import os
import tempfile
from datetime import datetime, timedelta, timezone

from sqlalchemy import create_engine, text

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


def _today():
    return datetime.now(timezone.utc).date()


def _ensure_profile(client):
    assert client.post("/profile", json=_PROFILE, headers=AUTH_HEADER).status_code == 200


def _log(client, day):
    r = client.post(
        "/food-logs",
        json={
            "log_date": day.isoformat(),
            "meal": "LUNCH",
            "grams": 100,
            "barcode": "123",
            "name": "Test",
            "kcal_100g": 100,
            "protein_100g": 1,
            "carbs_100g": 1,
            "fat_100g": 1,
        },
        headers=AUTH_HEADER,
    )
    assert r.status_code in (200, 201), r.text


def _get(client, today=None):
    return client.get(
        "/streak", params={"today": (today or _today()).isoformat()}, headers=AUTH_HEADER
    )


def _post(client, what, today=None):
    return client.post(
        "/streak/" + what,
        json={"today": (today or _today()).isoformat()},
        headers=AUTH_HEADER,
    )


def _insert_recovery(uid, gap_start, gap_end, kind, used_on):
    engine = create_engine(
        "sqlite:///" + os.path.join(tempfile.gettempdir(), "foodtracker_test.db")
    )
    with engine.begin() as conn:
        conn.execute(
            text(
                "INSERT INTO streak_recoveries (uid, gap_start, gap_end, kind, used_on) "
                "VALUES (:u, :gs, :ge, :k, :uo)"
            ),
            {"u": uid, "gs": gap_start, "ge": gap_end, "k": kind, "uo": used_on},
        )


def _setup_gap(client):
    T = _today()
    _ensure_profile(client)
    for i in range(4, 8):
        _log(client, T - timedelta(days=i))
    return T


def test_get_streak_empty(client):
    _ensure_profile(client)
    body = _get(client).json()
    assert body["current"] == 0
    assert body["next_milestone"] == 3
    assert len(body["last_14_days"]) == 14
    assert body["recovery_offer"] is None
    assert body["recovery_available_this_month"] is True


def test_get_streak_without_profile_is_zero(client):
    r = _get(client)
    assert r.status_code == 200
    assert r.json()["current"] == 0


def test_get_streak_counts_logs(client):
    T = _today()
    _ensure_profile(client)
    _log(client, T - timedelta(days=2))
    _log(client, T - timedelta(days=1))
    _log(client, T)
    _log(client, T)
    body = _get(client).json()
    assert body["current"] == 3
    assert body["total_logged_days"] == 3
    assert body["today_logged"] is True


def test_today_validation(client):
    T = _today()
    _ensure_profile(client)
    assert _get(client, T + timedelta(days=1)).status_code == 200
    far = T + timedelta(days=2)
    assert _get(client, far).status_code == 422
    assert _post(client, "recover", far).status_code == 422
    assert _post(client, "decline", far).status_code == 422


def test_recover_flow(client):
    T = _setup_gap(client)
    offer = _get(client).json()["recovery_offer"]
    assert offer["previous_streak"] == 4
    assert offer["gap_start"] == (T - timedelta(days=3)).isoformat()
    assert offer["gap_end"] == (T - timedelta(days=1)).isoformat()
    r = _post(client, "recover")
    assert r.status_code == 200
    body = r.json()
    assert body["current"] == 4
    assert body["recovery_offer"] is None
    assert body["recovery_available_this_month"] is False
    assert body["recovery_used_on"] == T.isoformat()


def test_recover_twice_conflict(client):
    _setup_gap(client)
    assert _post(client, "recover").status_code == 200
    assert _post(client, "recover").status_code == 409


def test_recover_without_offer_conflict(client):
    _ensure_profile(client)
    assert _post(client, "recover").status_code == 409


def test_decline_hides_offer_keeps_monthly(client):
    _setup_gap(client)
    r = _post(client, "decline")
    assert r.status_code == 200
    body = r.json()
    assert body["recovery_offer"] is None
    assert body["recovery_available_this_month"] is True
    assert _get(client).json()["recovery_offer"] is None


def test_decline_without_offer_conflict(client):
    _ensure_profile(client)
    assert _post(client, "decline").status_code == 409


def test_recover_requires_profile(client):
    assert _post(client, "recover").status_code == 404


def test_isolated_per_user(client):
    T = _setup_gap(client)
    _ensure_profile(client)
    _insert_recovery(
        "other-uid", T - timedelta(days=3), T - timedelta(days=1), "recovered", T
    )
    body = _get(client).json()
    assert body["recovery_available_this_month"] is True
    assert body["recovery_offer"] is not None


def test_recovery_allowed_next_month(client):
    T = _setup_gap(client)
    prev_month = T.replace(day=1) - timedelta(days=1)
    _insert_recovery(
        "test-uid-123",
        T - timedelta(days=40),
        T - timedelta(days=38),
        "recovered",
        prev_month,
    )
    body = _get(client).json()
    assert body["recovery_available_this_month"] is True
    assert body["recovery_used_on"] is None
    assert body["recovery_offer"] is not None
