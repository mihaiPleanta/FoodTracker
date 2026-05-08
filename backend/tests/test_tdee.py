AUTH_HEADER = {"Authorization": "Bearer fake-test-token"}


def test_calculate_tdee_male_moderate(client):
    # Male, 25 years, 75 kg, 180 cm, moderate activity
    # BMR = 10*75 + 6.25*180 - 5*25 + 5 = 750 + 1125 - 125 + 5 = 1755
    # TDEE = round(1755 * 1.55) = round(2720.25) = 2720
    response = client.post(
        "/calculate-tdee",
        json={"age": 25, "gender": "MALE", "height_cm": 180,
              "current_weight_kg": 75.0, "activity_level": "MODERATE"},
        headers=AUTH_HEADER,
    )
    assert response.status_code == 200
    assert response.json()["calorie_goal"] == 2720


def test_calculate_tdee_female_sedentary(client):
    # Female, 30 years, 60 kg, 165 cm, sedentary
    # BMR = 10*60 + 6.25*165 - 5*30 - 161 = 600 + 1031.25 - 150 - 161 = 1320.25
    # TDEE = round(1320.25 * 1.2) = round(1584.3) = 1584
    response = client.post(
        "/calculate-tdee",
        json={"age": 30, "gender": "FEMALE", "height_cm": 165,
              "current_weight_kg": 60.0, "activity_level": "SEDENTARY"},
        headers=AUTH_HEADER,
    )
    assert response.status_code == 200
    assert response.json()["calorie_goal"] == 1584


def test_calculate_tdee_requires_auth(client):
    response = client.post(
        "/calculate-tdee",
        json={"age": 25, "gender": "MALE", "height_cm": 180,
              "current_weight_kg": 75.0, "activity_level": "MODERATE"},
    )
    assert response.status_code == 422  # missing Authorization header
