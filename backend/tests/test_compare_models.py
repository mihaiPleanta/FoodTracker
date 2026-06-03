from schemas import RecipeDto
from scripts.compare_models import macro_consistency


def _recipe(**overrides) -> RecipeDto:
    base = dict(
        title="Test",
        description="desc",
        ingredients=[{"name": "ou", "quantity": "2 buc"}],
        steps=["pas 1", "pas 2"],
        servings=1,
        kcal_per_serving=200,
        protein_g=10,
        carbs_g=20,
        fat_g=5,
    )
    base.update(overrides)
    return RecipeDto.model_validate(base)


def test_macro_consistency_perfect_match():
    # 4*10 + 4*20 + 9*5 = 165; declared 165 -> 0.0 error
    r = _recipe(kcal_per_serving=165, protein_g=10, carbs_g=20, fat_g=5)
    assert macro_consistency(r) == 0.0


def test_macro_consistency_relative_error():
    # derived 165, declared 200 -> |200-165|/200 = 0.175
    r = _recipe(kcal_per_serving=200, protein_g=10, carbs_g=20, fat_g=5)
    assert abs(macro_consistency(r) - 0.175) < 1e-9


def test_macro_consistency_zero_kcal_returns_none():
    r = _recipe(kcal_per_serving=0)
    assert macro_consistency(r) is None
