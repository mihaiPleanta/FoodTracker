from datetime import date

from sqlalchemy.orm import Session

from models import FoodLog, HydrationLog
from tests.conftest import _sync_engine
from tests.test_food_logs import AUTH_HEADER as H, _VALID_LOG, _ensure_profile


def _get(client, frm, to):
    return client.get(f"/days?from={frm}&to={to}", headers=H)


def test_range_returns_only_days_with_data(client):
    _ensure_profile(client)
    client.post("/food-logs", json={**_VALID_LOG, "log_date": "2026-05-22"}, headers=H)
    client.post("/food-logs", json={**_VALID_LOG, "log_date": "2026-05-20"}, headers=H)
    client.put("/hydration/2026-05-21", json={"liters": 1.5}, headers=H)
    client.put("/hydration/2026-05-24", json={"liters": 0}, headers=H)
    client.post(
        "/weight-check-ins",
        json={"date": "2026-05-23", "weight_kg": 77.0},
        headers=H,
    )
    client.put("/days/2026-05-25/status", json={"complete": False}, headers=H)
    r = _get(client, "2026-05-19", "2026-05-26")
    assert r.status_code == 200, r.text
    days = r.json()["days"]
    assert [d["date"] for d in days] == [
        "2026-05-20", "2026-05-21", "2026-05-22", "2026-05-23", "2026-05-25",
    ]
    assert len(days[0]["foods_by_meal"]["breakfast"]) == 1
    assert days[1]["hydration_liters"] == 1.5
    assert days[3]["weight_check_in"] is not None
    assert days[4]["status"] == "incomplete"
    assert days[4]["goals"] is None


def test_range_includes_goal_snapshot_for_complete_day(client):
    _ensure_profile(client)
    client.post("/food-logs", json=_VALID_LOG, headers=H)
    client.put("/days/2026-05-20/status", json={"complete": True}, headers=H)
    goals = client.get("/goals", headers=H).json()
    day = _get(client, "2026-05-20", "2026-05-20").json()["days"][0]
    assert day["status"] == "complete"
    assert day["goals"] == {
        "calorie_goal": goals["calorie_goal"],
        "protein_goal_g": goals["protein_goal_g"],
        "carbs_goal_g": goals["carbs_goal_g"],
        "fat_goal_g": goals["fat_goal_g"],
        "tdee": goals["tdee"],
    }


def test_range_over_62_days_is_422(client):
    _ensure_profile(client)
    assert _get(client, "2026-01-01", "2026-03-04").status_code == 422
    assert _get(client, "2026-01-01", "2026-03-03").status_code == 200  # 62 days


def test_range_from_after_to_is_422(client):
    _ensure_profile(client)
    assert _get(client, "2026-05-21", "2026-05-20").status_code == 422


def test_range_isolated_per_uid(client):
    _ensure_profile(client)
    client.post("/food-logs", json=_VALID_LOG, headers=H)
    with Session(_sync_engine) as s:
        s.add(FoodLog(uid="other", client_id="x-1", log_date=date(2026, 5, 21), meal="LUNCH",
                      grams=100, barcode="9", name="X", categories=[], kcal_100g=1.0,
                      protein_100g=1.0, carbs_100g=1.0, fat_100g=1.0))
        s.add(HydrationLog(uid="other", log_date=date(2026, 5, 22), liters=2.0))
        s.commit()
    days = _get(client, "2026-05-19", "2026-05-23").json()["days"]
    assert [d["date"] for d in days] == ["2026-05-20"]


def test_range_logs_carry_client_id(client):
    _ensure_profile(client)
    client.post("/food-logs", json={**_VALID_LOG, "client_id": "c-9"}, headers=H)
    day = _get(client, "2026-05-20", "2026-05-20").json()["days"][0]
    log = day["foods_by_meal"]["breakfast"][0]
    assert log["client_id"] == "c-9"
    assert log["created_at"]
