from __future__ import annotations

import json
import random
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

_SYSTEM_PROMPT = """Ești un bucătar profesionist pentru o aplicație de nutriție.
Utilizatorul consumă des ingredientul-vedetă pe care ți-l dau. Fă o rețetă CLASICĂ și
binecunoscută, exact cum ar găti-o un om acasă, în care acel ingredient e elementul principal.

REGULI:
1. Construiește rețeta în jurul ingredientului-vedetă cerut.
2. Dacă ingredientul-vedetă este un fruct (banană, măr, fructe de pădure, căpșuni), fă un
   preparat DULCE potrivit pentru el: fulgi de ovăz, clătite, smoothie bowl, salată de fructe
   cu iaurt, budincă de chia sau pancakes. Alege unul singur și fă-l ca la carte.
3. Completează cu ingrediente obișnuite de cămară care merg NATURAL cu felul ales
   (legume, brânză, lactate, condimente, ulei, orez, paste, verdețuri etc.).
4. Rezultatul trebuie să fie un fel de mâncare real, recognoscibil dintr-o carte de bucate.
   Dacă ți se pare ciudat, ai greșit — alege un preparat clasic.
5. Rețeta să fie potrivită pentru masa cerută (la mic dejun ceva ușor, la cină ceva consistent).
6. Ține cont de obiectivul nutrițional al utilizatorului; porțiile să fie realiste.
7. Scrie în limba română corectă. Cantitățile să fie concrete (ex: "150 g", "2 linguri").
   Pașii de preparare clari și ordonați.

DESCRIEREA: maxim 12 cuvinte, spune doar ce conține preparatul, neutru și factual.
Exemplu bun: "Fulgi de ovăz cu banană, lapte și nuci."
Exemplu de evitat: "O rețetă delicioasă și hrănitoare, perfectă pentru tine."

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


def build_recipe_prompt(anchor, meal_type, goals) -> list[dict]:
    """Build the chat prompt around a single anchor food. Showing the model only
    ONE of the user's frequent foods (instead of the whole list) is what keeps
    recipes coherent: small models otherwise cram every listed food into one dish
    (e.g. banană + pui în omletă). The anchor still ties the recipe to the user's
    real eating habits; the model fills in classic complementary ingredients itself."""
    anchor_line = anchor.name + (f" ({anchor.brand})" if anchor.brand else "")
    meal_label = _MEAL_LABELS.get(meal_type, "masă")
    user_content = (
        f"Masa: {meal_label}.\n"
        f"Obiectiv nutrițional: {goals.mode}, {goals.calorie_goal} kcal/zi "
        f"(proteine {goals.protein_goal_g}g, carbohidrați {goals.carbs_goal_g}g, "
        f"grăsimi {goals.fat_goal_g}g).\n\n"
        f"Ingredient-vedetă (consumat des de utilizator): {anchor_line}.\n\n"
        f"Fă o rețetă clasică pentru {meal_label} cu acest ingredient principal."
    )
    return [
        {"role": "system", "content": _SYSTEM_PROMPT},
        {"role": "user", "content": user_content},
    ]


# Small models (e.g. gemma3:4b) occasionally emit JSON that doesn't match the
# recipe shape. Each attempt re-samples the model, so a quick retry almost
# always recovers — only surface RecipeParseError once every attempt has failed.
_MAX_PARSE_ATTEMPTS = 3


async def generate_recipe(db: AsyncSession, uid: str, meal_type: str, ollama) -> RecipeDto:
    top = await get_top_foods(db, uid)
    if len(top) < 3:
        raise InsufficientData()

    profile = (
        await db.execute(select(Profile).where(Profile.uid == uid))
    ).scalar_one()  # guaranteed: food_logs FK -> profiles
    goals = calculate_goals(profile)

    # Pick a single anchor food, biased toward the more frequent ones (which sit
    # first in `top`) but random so regenerating gives variety instead of always
    # anchoring on the #1 food.
    anchor = random.choices(top, weights=range(len(top), 0, -1), k=1)[0]
    messages = build_recipe_prompt(anchor, meal_type, goals)

    last_error: Exception | None = None
    for _ in range(_MAX_PARSE_ATTEMPTS):
        content = await ollama.generate_json(messages)
        try:
            data = json.loads(content)
            return RecipeDto.model_validate(data)
        except (json.JSONDecodeError, ValidationError, TypeError) as exc:
            last_error = exc
    raise RecipeParseError(str(last_error)) from last_error
