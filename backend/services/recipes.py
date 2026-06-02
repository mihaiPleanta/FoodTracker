from __future__ import annotations

import json
from datetime import date, timedelta

from pydantic import ValidationError
from sqlalchemy import desc, func, select
from sqlalchemy.ext.asyncio import AsyncSession

from models import FoodLog, Profile, SavedRecipe  # SavedRecipe used by the router import path
from schemas import RecipeDto, TopFood
from services.nutrition import calculate_goals


async def get_top_foods(
    db: AsyncSession, uid: str, *, days: int = 60, limit: int = 10
) -> list[TopFood]:
    """Most-consumed foods for a user: grouped by name, ordered by number of
    logs desc, then total grams desc. Macros come from the most recent log of
    each name."""
    cutoff = date.today() - timedelta(days=days)

    agg_stmt = (
        select(
            FoodLog.name,
            func.count(FoodLog.id).label("freq"),
            func.sum(FoodLog.grams).label("total_grams"),
        )
        .where(FoodLog.uid == uid, FoodLog.log_date >= cutoff)
        .group_by(FoodLog.name)
        .order_by(desc("freq"), desc("total_grams"))
        .limit(limit)
    )
    rows = (await db.execute(agg_stmt)).all()

    result: list[TopFood] = []
    for name, _freq, _total in rows:
        rep_stmt = (
            select(FoodLog)
            .where(FoodLog.uid == uid, FoodLog.name == name)
            .order_by(desc(FoodLog.created_at), desc(FoodLog.id))
            .limit(1)
        )
        rep = (await db.execute(rep_stmt)).scalar_one()
        result.append(
            TopFood(
                name=rep.name,
                brand=rep.brand,
                kcal_100g=rep.kcal_100g,
                protein_100g=rep.protein_100g,
                carbs_100g=rep.carbs_100g,
                fat_100g=rep.fat_100g,
            )
        )
    return result


_MEAL_LABELS = {
    "BREAKFAST": "mic dejun",
    "LUNCH": "prânz",
    "DINNER": "cină",
    "SNACKS": "gustare",
}

_SYSTEM_PROMPT = """Ești un asistent culinar pentru o aplicație de nutriție.
Generezi o rețetă folosind preponderent produsele pe care utilizatorul le consumă cel mai des.

REGULI:
1. Folosește în principal produsele din lista de mai jos. Poți adăuga 1-3 ingrediente comune (sare, ulei, condimente).
2. Rețeta trebuie să fie potrivită pentru masa cerută.
3. Ține cont de obiectivul nutrițional al utilizatorului; porțiile să fie realiste.
4. Scrie în limba română.
5. Cantitățile ingredientelor să fie concrete (ex: "150 g", "2 linguri").
6. Pașii de preparare să fie clari și ordonați.

Răspunde DOAR cu un obiect JSON valid, fără text în plus, exact în această formă:
{
  "title": "string",
  "description": "string",
  "ingredients": [{"name": "string", "quantity": "string"}],
  "steps": ["string"],
  "servings": 1,
  "kcal_per_serving": 0,
  "protein_g": 0,
  "carbs_g": 0,
  "fat_g": 0
}
"""


class InsufficientData(Exception):
    """User has fewer than 3 distinct logged foods."""


class RecipeParseError(Exception):
    """Ollama returned content that isn't a valid recipe."""


def build_recipe_prompt(top_foods, meal_type, goals) -> list[dict]:
    foods_lines = "\n".join(
        f"- {f.name}"
        + (f" ({f.brand})" if f.brand else "")
        + f": {f.kcal_100g:.0f} kcal, P {f.protein_100g:.1f}g,"
        f" C {f.carbs_100g:.1f}g, G {f.fat_100g:.1f}g / 100g"
        for f in top_foods
    )
    meal_label = _MEAL_LABELS.get(meal_type, "masă")
    user_content = (
        f"Masa: {meal_label}.\n"
        f"Obiectiv nutrițional: {goals.mode}, {goals.calorie_goal} kcal/zi "
        f"(proteine {goals.protein_goal_g}g, carbohidrați {goals.carbs_goal_g}g, "
        f"grăsimi {goals.fat_goal_g}g).\n\n"
        f"Produsele consumate cel mai des:\n{foods_lines}\n\n"
        f"Generează o rețetă pentru {meal_label}."
    )
    return [
        {"role": "system", "content": _SYSTEM_PROMPT},
        {"role": "user", "content": user_content},
    ]


async def generate_recipe(db: AsyncSession, uid: str, meal_type: str, ollama) -> RecipeDto:
    top = await get_top_foods(db, uid)
    if len(top) < 3:
        raise InsufficientData()

    profile = (
        await db.execute(select(Profile).where(Profile.uid == uid))
    ).scalar_one()  # guaranteed: food_logs FK -> profiles
    goals = calculate_goals(profile)

    messages = build_recipe_prompt(top, meal_type, goals)
    content = await ollama.generate_json(messages)

    try:
        data = json.loads(content)
        return RecipeDto.model_validate(data)
    except (json.JSONDecodeError, ValidationError, TypeError) as exc:
        raise RecipeParseError(str(exc)) from exc
