from __future__ import annotations

from schemas import RecipeDto


def macro_consistency(recipe: RecipeDto) -> float | None:
    """Relative error between declared kcal and macro-derived kcal
    (4*protein + 4*carbs + 9*fat). None when declared kcal <= 0."""
    if recipe.kcal_per_serving <= 0:
        return None
    derived = 4 * recipe.protein_g + 4 * recipe.carbs_g + 9 * recipe.fat_g
    return abs(recipe.kcal_per_serving - derived) / recipe.kcal_per_serving
