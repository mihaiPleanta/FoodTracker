from datetime import datetime, timezone

from sqlalchemy import create_engine
from sqlalchemy.orm import Session

from models import FoodLog, Profile
from tests.conftest import _TEST_DB_PATH

AUTH_HEADER = {"Authorization": "Bearer fake-test-token"}

_VALID_PROFILE = {
    "name": "Mihai",
    "age": 22,
    "gender": "MALE",
    "height_cm": 180,
    "current_weight_kg": 78.0,
    "target_weight_kg": 75.0,
    "activity_level": "MODERATE",
}

_VALID_LOG = {
    "log_date": "2026-05-20",
    "meal": "BREAKFAST",
    "grams": 150,
    "barcode": "3017620422003",
    "name": "Nutella",
    "brand": "Ferrero",
    "image_url": "https://example/nutella.jpg",
    "categories": ["Spreads"],
    "kcal_100g": 539.0,
    "protein_100g": 6.3,
    "carbs_100g": 57.5,
    "fat_100g": 30.9,
}


def _ensure_profile(client):
    client.post("/profile", json=_VALID_PROFILE, headers=AUTH_HEADER)


def _post(client, **overrides):
    r = client.post("/food-logs", json={**_VALID_LOG, **overrides}, headers=AUTH_HEADER)
    assert r.status_code == 201, r.text
    return r.json()


def _insert_direct(rows, extra_profiles=()):
    from datetime import date as _d
    engine = create_engine(f"sqlite:///{_TEST_DB_PATH}")
    with Session(engine) as s:
        for uid in extra_profiles:
            s.add(Profile(
                uid=uid, name="Other", age=30, gender="FEMALE", height_cm=170,
                current_weight_kg=60.0, target_weight_kg=58.0, activity_level="LIGHT",
            ))
        for r in rows:
            data = {**_VALID_LOG, **r}
            data["log_date"] = _d.fromisoformat(data["log_date"])
            s.add(FoodLog(**data))
        s.commit()
    engine.dispose()


def test_summary_groups_by_day_and_meal(client):
    _ensure_profile(client)
    _post(client)
    _post(client, meal="DINNER", grams=200, kcal_100g=100.0)
    _post(client, log_date="2026-05-18", meal="LUNCH", grams=100, kcal_100g=50.0)

    r = client.get("/food-logs/summary?from=2026-05-01&to=2026-05-31", headers=AUTH_HEADER)
    assert r.status_code == 200, r.text
    days = r.json()["days"]
    assert len(days) == 2
    assert days[0]["date"] == "2026-05-20"
    assert days[0]["meals"] == {
        "breakfast": {"count": 1, "kcal": 808},
        "dinner": {"count": 1, "kcal": 200},
    }
    assert days[1]["date"] == "2026-05-18"
    assert days[1]["meals"] == {"lunch": {"count": 1, "kcal": 50}}


def test_summary_excludes_out_of_range_and_other_users(client):
    _ensure_profile(client)
    _post(client, log_date="2026-04-30")
    _insert_direct([{"uid": "other", "log_date": "2026-05-20"}], extra_profiles=["other"])

    r = client.get("/food-logs/summary?from=2026-05-01&to=2026-05-31", headers=AUTH_HEADER)
    assert r.status_code == 200, r.text
    assert r.json() == {"days": []}


def test_summary_rejects_reversed_range(client):
    _ensure_profile(client)
    r = client.get("/food-logs/summary?from=2026-05-31&to=2026-05-01", headers=AUTH_HEADER)
    assert r.status_code == 422


def test_summary_rejects_range_over_62_days(client):
    _ensure_profile(client)
    r = client.get("/food-logs/summary?from=2026-01-01&to=2026-03-15", headers=AUTH_HEADER)
    assert r.status_code == 422


def test_get_day_orders_same_timestamp_by_id(client):
    _ensure_profile(client)
    ts = datetime(2026, 5, 20, 8, 0, tzinfo=timezone.utc)
    _insert_direct([
        {"uid": "test-uid-123", "name": "A", "created_at": ts},
        {"uid": "test-uid-123", "name": "B", "created_at": ts},
        {"uid": "test-uid-123", "name": "C", "created_at": ts},
    ])
    r = client.get("/days/2026-05-20", headers=AUTH_HEADER)
    assert r.status_code == 200, r.text
    names = [f["name"] for f in r.json()["foods_by_meal"]["breakfast"]]
    assert names == ["A", "B", "C"]


def test_day_query_orders_by_created_at_then_id():
    from datetime import date
    from routers.days import day_food_logs_query

    sql = str(day_food_logs_query("u", date(2026, 5, 20)))
    order_by = sql.split("ORDER BY", 1)[1]
    assert order_by.index("food_logs.created_at") < order_by.index("food_logs.id")
