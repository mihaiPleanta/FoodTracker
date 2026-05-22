import pytest
from services.nutrition import mifflin_st_jeor, calculate_goals


def test_mifflin_male():
    # 10*75 + 6.25*180 - 5*25 + 5 = 750 + 1125 - 125 + 5 = 1755
    assert mifflin_st_jeor(age=25, gender="MALE", height_cm=180, weight_kg=75.0) == pytest.approx(1755.0)


def test_mifflin_female():
    # 10*60 + 6.25*165 - 5*30 - 161 = 600 + 1031.25 - 150 - 161 = 1320.25
    assert mifflin_st_jeor(age=30, gender="FEMALE", height_cm=165, weight_kg=60.0) == pytest.approx(1320.25)


def test_mifflin_other():
    # Folosește formula de mijloc (-78)
    # 10*70 + 6.25*170 - 5*28 - 78 = 700 + 1062.5 - 140 - 78 = 1544.5
    assert mifflin_st_jeor(age=28, gender="OTHER", height_cm=170, weight_kg=70.0) == pytest.approx(1544.5)


class _FakeProfile:
    def __init__(self, **kwargs):
        for k, v in kwargs.items():
            setattr(self, k, v)


def _maintenance_profile():
    return _FakeProfile(
        age=25, gender="MALE", height_cm=180,
        current_weight_kg=75.0, target_weight_kg=75.0,
        activity_level="MODERATE",
    )


def test_calculate_goals_maintenance_mode():
    goals = calculate_goals(_maintenance_profile())
    assert goals.mode == "MAINTENANCE"
    # TDEE = 1755 * 1.55 = 2720.25 → 2720
    assert goals.calorie_goal == 2720


def test_calculate_goals_deficit_mode():
    p = _FakeProfile(
        age=25, gender="MALE", height_cm=180,
        current_weight_kg=75.0, target_weight_kg=70.0,  # vrea slăbire
        activity_level="MODERATE",
    )
    goals = calculate_goals(p)
    assert goals.mode == "DEFICIT"
    assert goals.calorie_goal == 2720 - 500  # 2220


def test_calculate_goals_surplus_mode():
    p = _FakeProfile(
        age=25, gender="MALE", height_cm=180,
        current_weight_kg=75.0, target_weight_kg=80.0,  # vrea masă
        activity_level="MODERATE",
    )
    goals = calculate_goals(p)
    assert goals.mode == "SURPLUS"
    assert goals.calorie_goal == 2720 + 300  # 3020


def test_calculate_goals_maintenance_within_tolerance():
    # diff = 0.5 kg → încă MAINTENANCE
    p = _FakeProfile(
        age=25, gender="MALE", height_cm=180,
        current_weight_kg=75.0, target_weight_kg=75.5,
        activity_level="MODERATE",
    )
    assert calculate_goals(p).mode == "MAINTENANCE"


def test_calculate_goals_clamps_to_1200_min():
    # Femeie scundă, sedentară, deficit agresiv
    p = _FakeProfile(
        age=60, gender="FEMALE", height_cm=150,
        current_weight_kg=50.0, target_weight_kg=42.0,
        activity_level="SEDENTARY",
    )
    goals = calculate_goals(p)
    # BMR = 500 + 937.5 - 300 - 161 = 976.5
    # TDEE = 976.5 * 1.2 = 1171.8
    # Cu deficit -500 → 671.8 (sub 1200, ar trebui clamp)
    assert goals.calorie_goal == 1200


def test_calculate_goals_clamps_to_5000_max():
    # Caz patologic: greutate uriașă, VERY_ACTIVE
    p = _FakeProfile(
        age=25, gender="MALE", height_cm=200,
        current_weight_kg=300.0, target_weight_kg=320.0,
        activity_level="VERY_ACTIVE",
    )
    goals = calculate_goals(p)
    assert goals.calorie_goal == 5000


def test_calculate_goals_macros_sum_approx_calorie_goal():
    goals = calculate_goals(_maintenance_profile())
    # 4*P + 4*C + 9*F ≈ calorie_goal cu toleranță de rounding
    macros_kcal = goals.protein_goal_g * 4 + goals.carbs_goal_g * 4 + goals.fat_goal_g * 9
    assert abs(macros_kcal - goals.calorie_goal) <= 10


def test_calculate_goals_protein_from_current_weight():
    goals = calculate_goals(_maintenance_profile())  # current = 75 kg
    # 1.8 * 75 = 135
    assert goals.protein_goal_g == 135


def test_calculate_goals_unknown_activity_falls_back_to_moderate():
    p = _FakeProfile(
        age=25, gender="MALE", height_cm=180,
        current_weight_kg=75.0, target_weight_kg=75.0,
        activity_level="UNKNOWN_LEVEL",
    )
    goals = calculate_goals(p)
    # Folosește 1.55 (MODERATE)
    assert goals.calorie_goal == 2720
