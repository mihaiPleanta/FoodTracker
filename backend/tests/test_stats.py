from datetime import date, timedelta

from sqlalchemy.orm import Session

from models import FoodLog, HydrationLog
from tests.conftest import _sync_engine

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

_LOG = {
    "log_date": "2026-05-20",
    "meal": "LUNCH",
    "grams": 200,
    "barcode": "111",
    "name": "Yogurt",
    "categories": [],
    "kcal_100g": 60.0,
    "protein_100g": 4.0,
    "carbs_100g": 5.0,
    "fat_100g": 2.0,
}


def _profile(client):
    assert client.post("/profile", json=_PROFILE, headers=AUTH_HEADER).status_code == 200


def _log(client, **kw):
    r = client.post("/food-logs", json={**_LOG, **kw}, headers=AUTH_HEADER)
    assert r.status_code in (200, 201), r.text


def _get(client, frm, to):
    return client.get("/stats/days?from=%s&to=%s" % (frm, to), headers=AUTH_HEADER)


def _status(client, day, complete):
    r = client.put("/days/%s/status" % day, json={"complete": complete}, headers=AUTH_HEADER)
    assert r.status_code == 200, r.text


def test_stats_days_aggregates_logs_per_day(client):
    _profile(client)
    _log(client)
    _log(client, grams=50, kcal_100g=100.0, protein_100g=10.0, carbs_100g=20.0,
         fat_100g=1.0, name="Bar", barcode="222")
    r = _get(client, "2026-05-20", "2026-05-20")
    assert r.status_code == 200
    body = r.json()
    assert len(body["days"]) == 1
    d = body["days"][0]
    assert d["date"] == "2026-05-20"
    assert d["kcal"] == 170
    assert d["protein_g"] == 13.0
    assert d["carbs_g"] == 20.0
    assert d["fat_g"] == 4.5
    assert d["has_food"] is True
    assert d["status"] is None
    assert d["goals"] is None
    assert d["hydration_l"] == 0.0
    assert body["top_foods"] == {"by_kcal": [], "by_protein": []}


def test_stats_days_includes_status_and_snapshot(client):
    _profile(client)
    _log(client)
    _status(client, "2026-05-20", True)
    _log(client, log_date="2026-05-21")
    _status(client, "2026-05-21", False)
    days = _get(client, "2026-05-20", "2026-05-21").json()["days"]
    assert [d["date"] for d in days] == ["2026-05-20", "2026-05-21"]
    assert days[0]["status"] == "complete"
    g = days[0]["goals"]
    assert set(g) == {"calorie_goal", "protein_goal_g", "carbs_goal_g", "fat_goal_g", "tdee"}
    assert all(isinstance(v, int) and v > 0 for v in g.values())
    assert days[1]["status"] == "incomplete"
    assert days[1]["goals"] is None


def test_stats_days_complete_day_without_logs_has_has_food_false(client):
    _profile(client)
    _status(client, "2026-05-20", True)
    days = _get(client, "2026-05-20", "2026-05-20").json()["days"]
    assert len(days) == 1
    assert days[0]["has_food"] is False
    assert days[0]["kcal"] == 0
    assert days[0]["status"] == "complete"
    assert days[0]["goals"] is not None


def test_stats_days_hydration_only_day_present(client):
    _profile(client)
    client.put("/hydration/2026-05-20", json={"liters": 1.5}, headers=AUTH_HEADER)
    client.put("/hydration/2026-05-21", json={"liters": 0}, headers=AUTH_HEADER)
    days = _get(client, "2026-05-20", "2026-05-22").json()["days"]
    assert len(days) == 1
    assert days[0]["date"] == "2026-05-20"
    assert days[0]["hydration_l"] == 1.5
    assert days[0]["has_food"] is False


def test_stats_days_excludes_out_of_range(client):
    _profile(client)
    _log(client, log_date="2026-05-19")
    _log(client, log_date="2026-05-20")
    _log(client, log_date="2026-05-22")
    days = _get(client, "2026-05-20", "2026-05-21").json()["days"]
    assert [d["date"] for d in days] == ["2026-05-20"]


def test_stats_days_weights_include_60_day_warmup(client):
    _profile(client)
    frm = date(2026, 5, 20)
    for offset, w in [(-70, 90.0), (-50, 80.0), (0, 78.0), (3, 77.0)]:
        client.post(
            "/weight-check-ins",
            json={"date": str(frm + timedelta(days=offset)), "weight_kg": w},
            headers=AUTH_HEADER,
        )
    weights = _get(client, str(frm), str(frm + timedelta(days=1))).json()["weights"]
    assert weights == [
        {"date": str(frm - timedelta(days=50)), "weight_kg": 80.0},
        {"date": str(frm), "weight_kg": 78.0},
    ]


def test_stats_days_range_over_62_is_422(client):
    _profile(client)
    assert _get(client, "2026-01-01", "2026-03-04").status_code == 422  # 63 days
    assert _get(client, "2026-01-01", "2026-03-03").status_code == 200  # 62 days


def test_stats_days_from_after_to_is_422(client):
    _profile(client)
    assert _get(client, "2026-05-21", "2026-05-20").status_code == 422


def test_stats_days_per_user(client):
    _profile(client)
    _log(client)
    with Session(_sync_engine) as s:
        s.add(FoodLog(uid="other", log_date=date(2026, 5, 21), meal="LUNCH", grams=100,
                      barcode="9", name="X", categories=[], kcal_100g=1.0,
                      protein_100g=1.0, carbs_100g=1.0, fat_100g=1.0))
        s.add(HydrationLog(uid="other", log_date=date(2026, 5, 22), liters=2.0))
        s.commit()
    days = _get(client, "2026-05-20", "2026-05-23").json()["days"]
    assert [d["date"] for d in days] == ["2026-05-20"]


def _top(client, frm="2026-05-01", to="2026-05-31"):
    r = _get(client, frm, to)
    assert r.status_code == 200, r.text
    return r.json()["top_foods"]


def test_top_foods_only_from_complete_days(client):
    _profile(client)
    _log(client, log_date="2026-05-20")
    _log(client, log_date="2026-05-21", barcode="222", name="Bar")
    _status(client, "2026-05-20", True)
    top = _top(client)
    assert [f["key"] for f in top["by_kcal"]] == ["111"]


def test_top_foods_sum_across_days_and_count_days(client):
    _profile(client)
    for d in ("2026-05-20", "2026-05-21"):
        _log(client, log_date=d)
        _status(client, d, True)
    _log(client, log_date="2026-05-21")  # second log same day
    top = _top(client)
    f = top["by_kcal"][0]
    assert f["key"] == "111"
    assert f["total_kcal"] == 360  # 3 x 200g x 60/100
    assert f["total_protein_g"] == 24.0
    assert f["days_count"] == 2


def test_top_foods_groups_recipes_by_name(client):
    _profile(client)
    _log(client, barcode="recipe", name="Pasta")
    _log(client, barcode="recipe", name="Soup")
    _status(client, "2026-05-20", True)
    top = _top(client)
    assert sorted(f["key"] for f in top["by_kcal"]) == ["recipe:Pasta", "recipe:Soup"]


def test_top_foods_limit_5(client):
    _profile(client)
    for i in range(7):
        _log(client, barcode="b%d" % i, name="F%d" % i)
    _status(client, "2026-05-20", True)
    top = _top(client)
    assert len(top["by_kcal"]) == 5
    assert len(top["by_protein"]) == 5


def test_top_foods_by_protein_order(client):
    _profile(client)
    _log(client, barcode="a", name="A", kcal_100g=500.0, protein_100g=1.0)
    _log(client, barcode="b", name="B", kcal_100g=50.0, protein_100g=30.0)
    _status(client, "2026-05-20", True)
    top = _top(client)
    assert [f["key"] for f in top["by_kcal"]] == ["a", "b"]
    assert [f["key"] for f in top["by_protein"]] == ["b", "a"]
