AUTH_HEADER = {"Authorization": "Bearer fake-test-token"}


def test_goals_returns_404_when_profile_missing(client):
    response = client.get("/goals", headers=AUTH_HEADER)
    assert response.status_code == 404


def _create_profile(client, **overrides):
    payload = {
        "name": "Test User",
        "age": 25,
        "gender": "MALE",
        "height_cm": 180,
        "current_weight_kg": 75.0,
        "target_weight_kg": 75.0,
        "activity_level": "MODERATE",
    }
    payload.update(overrides)
    response = client.post("/profile", json=payload, headers=AUTH_HEADER)
    assert response.status_code == 200
    return response


def test_goals_maintenance(client):
    _create_profile(client)  # current == target
    response = client.get("/goals", headers=AUTH_HEADER)
    assert response.status_code == 200
    data = response.json()
    assert data["mode"] == "MAINTENANCE"
    assert data["calorie_goal"] == 2720


def test_goals_deficit(client):
    _create_profile(client, target_weight_kg=70.0)
    response = client.get("/goals", headers=AUTH_HEADER)
    data = response.json()
    assert data["mode"] == "DEFICIT"
    assert data["calorie_goal"] == 2720 - 500


def test_goals_surplus(client):
    _create_profile(client, target_weight_kg=80.0)
    response = client.get("/goals", headers=AUTH_HEADER)
    data = response.json()
    assert data["mode"] == "SURPLUS"
    assert data["calorie_goal"] == 2720 + 300


def test_goals_clamps_to_1200_minimum(client):
    _create_profile(
        client,
        age=60, gender="FEMALE", height_cm=150,
        current_weight_kg=50.0, target_weight_kg=42.0,
        activity_level="SEDENTARY",
    )
    response = client.get("/goals", headers=AUTH_HEADER)
    assert response.json()["calorie_goal"] == 1200


def test_goals_requires_auth(client):
    response = client.get("/goals")
    assert response.status_code == 422  # missing Authorization header


def test_goals_macros_present_and_consistent(client):
    _create_profile(client)
    data = client.get("/goals", headers=AUTH_HEADER).json()
    assert data["protein_goal_g"] == 135  # 1.8 * 75
    assert data["fat_goal_g"] > 0
    assert data["carbs_goal_g"] > 0
    macros_kcal = data["protein_goal_g"] * 4 + data["carbs_goal_g"] * 4 + data["fat_goal_g"] * 9
    assert abs(macros_kcal - data["calorie_goal"]) <= 10


def test_goals_includes_auto_hydration(client):
    _create_profile(client)
    h = client.get("/goals", headers=AUTH_HEADER).json()["hydration"]
    assert h == {
        "goal_l": 3.1,
        "auto_l": 3.1,
        "is_manual": False,
        "weight_part_l": 2.63,
        "activity_bonus_l": 0.5,
        "creatine_bonus_l": 0.0,
    }


def test_goals_hydration_manual(client):
    _create_profile(client, takes_creatine=True, hydration_goal_override_l=2.8)
    h = client.get("/goals", headers=AUTH_HEADER).json()["hydration"]
    assert h["goal_l"] == 2.8 and h["is_manual"] and h["auto_l"] == 3.6


def test_goals_include_tdee(client):
    from services.nutrition import calculate_goals
    from models import Profile
    from sqlalchemy.orm import Session
    from tests.conftest import _sync_engine

    _create_profile(client)
    data = client.get("/goals", headers=AUTH_HEADER).json()
    with Session(_sync_engine) as s:
        profile = s.query(Profile).filter_by(uid="test-uid-123").one()
        expected = calculate_goals(profile).tdee
    assert data["tdee"] == round(expected)
