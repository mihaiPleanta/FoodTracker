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


_ING = [
    {"name": "Orez", "grams": 200, "kcal_100g": 130.0, "protein_100g": 2.7,
     "carbs_100g": 28.0, "fat_100g": 0.3},
    {"name": "Pui", "grams": 100, "kcal_100g": 165.0, "protein_100g": 31.0,
     "carbs_100g": 0.0, "fat_100g": 3.6},
]


def _copy(client, items, to_date="2026-05-21"):
    return client.post(
        "/food-logs/copy", json={"to_date": to_date, "items": items}, headers=AUTH_HEADER
    )


def test_copy_creates_rows_on_target_date_with_source_meal(client):
    _ensure_profile(client)
    src = _post(client)
    r = _copy(client, [{"source_id": src["id"], "grams": 100}])
    assert r.status_code == 201, r.text
    c = r.json()[0]
    assert c["log_date"] == "2026-05-21"
    assert c["meal"] == "BREAKFAST"
    assert c["grams"] == 100
    for k in ("name", "brand", "barcode", "categories", "image_url"):
        assert c[k] == _VALID_LOG[k]
    assert c["kcal_100g"] == 539.0
    assert c["id"] != src["id"]
    day = client.get("/days/2026-05-20", headers=AUTH_HEADER).json()
    assert day["foods_by_meal"]["breakfast"][0]["grams"] == 150


def test_copy_keeps_unit_label_from_item(client):
    _ensure_profile(client)
    src = _post(client)
    r = _copy(client, [{"source_id": src["id"], "grams": 30, "unit": "lingură", "unit_qty": 2}])
    assert r.status_code == 201, r.text
    assert r.json()[0]["unit"] == "lingură"
    assert r.json()[0]["unit_qty"] == 2


def test_copy_rejects_unit_without_qty(client):
    _ensure_profile(client)
    src = _post(client)
    r = _copy(client, [{"source_id": src["id"], "grams": 30, "unit": "lingură"}])
    assert r.status_code == 422


def test_copy_preserves_order_of_items(client):
    _ensure_profile(client)
    a = _post(client, name="a")
    b = _post(client, name="b")
    c = _post(client, name="c")
    items = [{"source_id": x["id"], "grams": 10} for x in (c, a, b)]
    r = _copy(client, items)
    assert r.status_code == 201, r.text
    assert [x["name"] for x in r.json()] == ["c", "a", "b"]
    day = client.get("/days/2026-05-21", headers=AUTH_HEADER).json()
    assert [x["name"] for x in day["foods_by_meal"]["breakfast"]] == ["c", "a", "b"]


def test_copy_unedited_recipe_copies_source_exactly(client):
    _ensure_profile(client)
    src = _post(client, grams=300, ingredients=_ING, kcal_100g=148.3, protein_100g=12.1,
                carbs_100g=18.7, fat_100g=1.4)
    r = _copy(client, [{"source_id": src["id"], "grams": 999, "unit": "bol", "unit_qty": 1}])
    assert r.status_code == 201, r.text
    c = r.json()[0]
    assert c["grams"] == 300
    assert c["ingredients"] == src["ingredients"]
    assert c["kcal_100g"] == 148.3
    assert c["protein_100g"] == 12.1
    assert c["carbs_100g"] == 18.7
    assert c["fat_100g"] == 1.4
    assert c["unit"] is None and c["unit_qty"] is None


def test_copy_edited_recipe_recomputes_totals(client):
    _ensure_profile(client)
    src = _post(client, grams=300, ingredients=_ING)
    other = _post(client, grams=300, ingredients=_ING)
    new_ing = [{**_ING[0], "grams": 50}, {**_ING[1], "grams": 400}]
    expected = client.patch(
        f"/food-logs/{other['id']}", json={"grams": 1, "ingredients": new_ing}, headers=AUTH_HEADER
    ).json()
    r = _copy(client, [{"source_id": src["id"], "grams": 5, "unit": "bol", "unit_qty": 1,
                        "ingredients": new_ing}])
    assert r.status_code == 201, r.text
    c = r.json()[0]
    assert c["grams"] == expected["grams"] == 450
    for k in ("kcal_100g", "protein_100g", "carbs_100g", "fat_100g"):
        assert c[k] == expected[k]
    assert len(c["ingredients"]) == 2
    assert c["unit"] is None and c["unit_qty"] is None


def test_copy_foreign_source_returns_404_and_inserts_nothing(client):
    _ensure_profile(client)
    mine = _post(client)
    _insert_direct([{"uid": "other", "log_date": "2026-05-20"}], extra_profiles=["other"])
    from sqlalchemy import select
    engine = create_engine(f"sqlite:///{_TEST_DB_PATH}")
    with Session(engine) as s:
        foreign_id = s.execute(select(FoodLog.id).where(FoodLog.uid == "other")).scalar_one()
    engine.dispose()
    r = _copy(client, [{"source_id": mine["id"], "grams": 10},
                       {"source_id": foreign_id, "grams": 10}])
    assert r.status_code == 404
    day = client.get("/days/2026-05-21", headers=AUTH_HEADER).json()
    assert all(v == [] for v in day["foods_by_meal"].values())


def test_copy_missing_source_returns_404(client):
    _ensure_profile(client)
    r = _copy(client, [{"source_id": 99999, "grams": 10}])
    assert r.status_code == 404


def test_copy_empty_items_returns_422(client):
    _ensure_profile(client)
    assert _copy(client, []).status_code == 422


def test_copy_over_100_items_returns_422(client):
    _ensure_profile(client)
    src = _post(client)
    items = [{"source_id": src["id"], "grams": 10}] * 101
    assert _copy(client, items).status_code == 422
