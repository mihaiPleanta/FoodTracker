import pytest
from services.nutrition import mifflin_st_jeor, calculate_goals, calculate_hydration_goal


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


def _hp(kg, activity, creatine=False, override=None):
    return _FakeProfile(
        current_weight_kg=kg, activity_level=activity,
        takes_creatine=creatine, hydration_goal_override_l=override,
    )


def test_hydration_70kg_sedentary():
    g = calculate_hydration_goal(_hp(70, "SEDENTARY"))
    assert g.goal_l == 2.5 and g.auto_l == 2.5 and not g.is_manual


def test_hydration_70kg_moderate():
    assert calculate_hydration_goal(_hp(70, "MODERATE")).goal_l == 3.0  # 2.95 -> half-up


def test_hydration_70kg_moderate_creatine():
    g = calculate_hydration_goal(_hp(70, "MODERATE", creatine=True))
    assert g.goal_l == 3.5 and g.weight_part_l == 2.45
    assert g.activity_bonus_l == 0.5 and g.creatine_bonus_l == 0.5


def test_hydration_clamps_low():
    assert calculate_hydration_goal(_hp(40, "SEDENTARY")).goal_l == 1.5


def test_hydration_clamps_high():
    assert calculate_hydration_goal(_hp(150, "VERY_ACTIVE", creatine=True)).goal_l == 5.0


def test_hydration_unknown_activity_as_moderate():
    assert calculate_hydration_goal(_hp(70, "WHATEVER")).goal_l == 3.0


def test_hydration_override_wins_and_ignores_creatine():
    g = calculate_hydration_goal(_hp(70, "MODERATE", creatine=True, override=3.0))
    assert g.goal_l == 3.0 and g.is_manual and g.auto_l == 3.5 and g.creatine_bonus_l == 0.5


def test_hydration_override_independent_of_weight():
    assert calculate_hydration_goal(_hp(95, "ACTIVE", override=2.2)).goal_l == 2.2


def test_calculate_goals_exposes_tdee():
    profile = _FakeProfile(
        age=22, gender="MALE", height_cm=180,
        current_weight_kg=78.0, target_weight_kg=78.0,
        activity_level="MODERATE",
    )
    bmr = mifflin_st_jeor(age=22, gender="MALE", height_cm=180, weight_kg=78.0)
    goals = calculate_goals(profile)
    assert goals.tdee == round(bmr * 1.55)
    assert goals.calorie_goal == goals.tdee

    deficit = _FakeProfile(
        age=22, gender="MALE", height_cm=180,
        current_weight_kg=78.0, target_weight_kg=70.0,
        activity_level="MODERATE",
    )
    deficit_goals = calculate_goals(deficit)
    assert deficit_goals.tdee == goals.tdee
    assert abs(deficit_goals.calorie_goal - max(1200, min(deficit_goals.tdee - 500, 5000))) <= 1
