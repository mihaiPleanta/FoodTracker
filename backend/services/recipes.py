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


# Keywords (RO+EN) that mark a food as sweet/fruit — such anchors are skipped for
# lunch/dinner so the model isn't forced into odd dishes (e.g. banana salad for lunch).
# Ambiguous barewords (e.g. "măr" vs "mărar") are intentionally omitted; apples are
# caught via the "fruits"/"fructe" category or the "apple" keyword.
_SWEET_KEYWORDS = frozenset({
    "banan", "fruct", "fruit", "berry", "berries", "strawberr", "căpșun", "capsun",
    "zmeur", "raspberr", "afin", "blueberr", "struguri", "grape", "ananas",
    "pineapple", "mango", "pepene", "melon", "cireș", "cires", "cherry",
    "piersic", "peach", "miere", "honey", "ciocolat", "chocolate", "apple",
    "desert", "dessert", "gem", "jam", "prăjitur", "prajitur", "biscuit",
    "cookie", "smoothie", "dulce",
})


def _is_sweet_anchor(food) -> bool:
    """True if the food's name or any category contains a sweet/fruit keyword."""
    haystacks = [food.name.lower()] + [c.lower() for c in (food.categories or [])]
    return any(kw in hay for hay in haystacks for kw in _SWEET_KEYWORDS)


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
                categories=rep.categories or [],
            )
        )
    return result


_MEAL_LABELS = {
    "BREAKFAST": "mic dejun",
    "LUNCH": "prânz",
    "DINNER": "cină",
    "SNACKS": "gustare",
}

_MEAL_LABELS_EN = {
    "BREAKFAST": "breakfast",
    "LUNCH": "lunch",
    "DINNER": "dinner",
    "SNACKS": "snack",
}

_SYSTEM_PROMPT = """Ești un bucătar profesionist pentru o aplicație de nutriție.
Utilizatorul consumă des ingredientul-vedetă pe care ți-l dau. Fă o rețetă CLASICĂ și
binecunoscută, exact cum ar găti-o un om acasă, în care acel ingredient e elementul principal.

REGULI:
1. Construiește rețeta în jurul ingredientului-vedetă cerut.
2. Dacă ingredientul-vedetă este un fruct (banană, măr, fructe de pădure, căpșuni), fă un
   preparat DULCE potrivit pentru el: fulgi de ovăz, clătite americane, smoothie bowl,
   salată de fructe cu iaurt sau budincă de chia. Alege unul singur și fă-l ca la carte.
3. Completează cu ingrediente obișnuite de cămară care merg NATURAL cu felul ales
   (legume, brânză, lactate, condimente, ulei, orez, paste, verdețuri etc.).
4. Rezultatul trebuie să fie un fel de mâncare real, recognoscibil dintr-o carte de bucate.
   Dacă ți se pare ciudat, ai greșit — alege un preparat clasic.
5. Rețeta să fie potrivită pentru masa cerută și să se încadreze în bugetul caloric indicat.
6. Scrie TITLUL și toate numele de ingrediente în limba română. Fără cuvinte în engleză.
7. Scrie în limba română corectă. Pașii de preparare clari și ordonați.
8. Pentru FIECARE ingredient dă: numele, cantitatea în GRAME (număr întreg, fără unități text)
   și valorile nutriționale la 100 g (kcal, proteine, carbohidrați, grăsimi). Convertește
   în grame orice cantitate (ex: "2 linguri ulei" → grams: 30; "2 ouă" → grams: 100;
   "1 cană lapte" → grams: 240). Estimează macros realiste.

DESCRIEREA: maxim 12 cuvinte, spune doar ce conține preparatul, neutru și factual.
Exemplu bun: "Fulgi de ovăz cu banană, lapte și nuci."
Exemplu de evitat: "O rețetă delicioasă și hrănitoare, perfectă pentru tine."

Răspunde DOAR cu un obiect JSON valid, fără text în plus, exact în această formă:
{
  "title": "string",
  "description": "string",
  "ingredients": [
    {"name": "string", "grams": 0, "kcal_100g": 0, "protein_100g": 0, "carbs_100g": 0, "fat_100g": 0}
  ],
  "steps": ["string"],
  "servings": 1,
  "kcal_per_serving": 0,
  "protein_g": 0,
  "carbs_g": 0,
  "fat_g": 0
}
"""

# English mirror of _SYSTEM_PROMPT — same single-anchor design + positive fruit rule
# + concise-description constraint, so recipe quality is preserved when the app is in EN.
_SYSTEM_PROMPT_EN = """You are a professional chef for a nutrition app.
The user frequently eats the star ingredient I give you. Make a CLASSIC, well-known recipe,
exactly how someone would cook it at home, in which that ingredient is the main element.

RULES:
1. Build the recipe around the requested star ingredient.
2. If the star ingredient is a fruit (banana, apple, berries, strawberries), make a SWEET
   dish suited to it: oatmeal, American pancakes, smoothie bowl, fruit salad with yogurt
   or chia pudding. Pick a single one and make it properly.
3. Complete it with common pantry ingredients that go NATURALLY with the chosen dish
   (vegetables, cheese, dairy, spices, oil, rice, pasta, greens, etc.).
4. The result must be a real dish, recognizable from a cookbook.
   If it seems weird, you got it wrong — pick a classic dish.
5. Make the recipe fit the requested meal and stay within the indicated calorie budget.
6. Write in correct English. Clear, ordered preparation steps.
7. For EACH ingredient give: the name, the amount in GRAMS (a whole number, no text units)
   and the nutrition per 100 g (kcal, protein, carbs, fat). Convert any quantity to grams
   (e.g. "2 tablespoons oil" → grams: 30; "2 eggs" → grams: 100;
   "1 cup milk" → grams: 240). Estimate realistic macros.

THE DESCRIPTION: max 12 words, state only what the dish contains, neutral and factual.
Good example: "Oatmeal with banana, milk and walnuts."
Avoid: "A delicious and nutritious recipe, perfect for you."

Respond ONLY with a valid JSON object, no extra text, in exactly this shape:
{
  "title": "string",
  "description": "string",
  "ingredients": [
    {"name": "string", "grams": 0, "kcal_100g": 0, "protein_100g": 0, "carbs_100g": 0, "fat_100g": 0}
  ],
  "steps": ["string"],
  "servings": 1,
  "kcal_per_serving": 0,
  "protein_g": 0,
  "carbs_g": 0,
  "fat_g": 0
}
"""


def _recipe_totals(ingredients) -> tuple[int, int, int, int]:
    """Sum per-serving macros from per-100g ingredient macros × their grams."""
    kcal = sum(i.kcal_100g * i.grams / 100 for i in ingredients)
    p = sum(i.protein_100g * i.grams / 100 for i in ingredients)
    c = sum(i.carbs_100g * i.grams / 100 for i in ingredients)
    f = sum(i.fat_100g * i.grams / 100 for i in ingredients)
    return round(kcal), round(p), round(c), round(f)


class InsufficientData(Exception):
    """User has fewer than 3 distinct logged foods."""


class RecipeParseError(Exception):
    """Ollama returned content that isn't a valid recipe."""


# Fraction of daily calories allocated per meal type.
_MEAL_KCAL_RATIO = {
    "BREAKFAST": 0.25,
    "LUNCH":     0.35,
    "DINNER":    0.30,
    "SNACKS":    0.10,
}


def build_recipe_prompt(anchor, meal_type, goals, language: str = "ro") -> list[dict]:
    """Build the chat prompt around a single anchor food. Showing the model only
    ONE of the user's frequent foods (instead of the whole list) is what keeps
    recipes coherent: small models otherwise cram every listed food into one dish
    (e.g. banană + pui în omletă). The anchor still ties the recipe to the user's
    real eating habits; the model fills in classic complementary ingredients itself.

    `language` ("ro"/"en") follows the in-app language so the generated recipe text
    matches the rest of the UI."""
    anchor_line = anchor.name + (f" ({anchor.brand})" if anchor.brand else "")
    meal_kcal = round(goals.calorie_goal * _MEAL_KCAL_RATIO.get(meal_type, 0.25))
    if language == "en":
        meal_label = _MEAL_LABELS_EN.get(meal_type, "meal")
        system_prompt = _SYSTEM_PROMPT_EN
        user_content = (
            f"Meal: {meal_label}.\n"
            f"Calorie budget for this meal: approximately {meal_kcal} kcal "
            f"(daily goal: {goals.mode}, {goals.calorie_goal} kcal/day).\n"
            f"Macro targets/day: protein {goals.protein_goal_g}g, "
            f"carbs {goals.carbs_goal_g}g, fat {goals.fat_goal_g}g.\n\n"
            f"Star ingredient (frequently eaten by the user): {anchor_line}.\n\n"
            f"Make a classic {meal_label} recipe with this main ingredient."
        )
    else:
        meal_label = _MEAL_LABELS.get(meal_type, "masă")
        system_prompt = _SYSTEM_PROMPT
        user_content = (
            f"Masa: {meal_label}.\n"
            f"Buget caloric pentru această masă: aproximativ {meal_kcal} kcal "
            f"(obiectiv zilnic: {goals.mode}, {goals.calorie_goal} kcal/zi).\n"
            f"Macros țintă/zi: proteine {goals.protein_goal_g}g, "
            f"carbohidrați {goals.carbs_goal_g}g, grăsimi {goals.fat_goal_g}g.\n\n"
            f"Ingredient-vedetă (consumat des de utilizator): {anchor_line}.\n\n"
            f"Fă o rețetă clasică pentru {meal_label} cu acest ingredient principal."
        )
    return [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": user_content},
    ]


# Small models (e.g. gemma3:4b) occasionally emit JSON that doesn't match the
# recipe shape. Each attempt re-samples the model, so a quick retry almost
# always recovers — only surface RecipeParseError once every attempt has failed.
_MAX_PARSE_ATTEMPTS = 3


async def generate_recipe(
    db: AsyncSession, uid: str, meal_type: str, ollama, language: str = "ro",
    exclude_anchor: str | None = None,
) -> RecipeDto:
    top = await get_top_foods(db, uid)
    if len(top) < 3:
        raise InsufficientData()

    profile = (
        await db.execute(select(Profile).where(Profile.uid == uid))
    ).scalar_one()  # guaranteed: food_logs FK -> profiles
    goals = calculate_goals(profile)

    # Meal-fit: lunch/dinner skip sweet/fruit anchors (fallback to full list if that
    # empties the pool). Breakfast/snacks allow them.
    candidates = list(top)
    if meal_type in ("LUNCH", "DINNER"):
        savory = [f for f in candidates if not _is_sweet_anchor(f)]
        if savory:
            candidates = savory

    # Variety: avoid repeating the previous anchor when an alternative exists.
    if exclude_anchor:
        pruned = [f for f in candidates if f.name != exclude_anchor]
        if pruned:
            candidates = pruned

    # Uniform pick over the filtered pool — still a habitual food, but varied.
    anchor = random.choice(candidates)
    messages = build_recipe_prompt(anchor, meal_type, goals, language)

    last_error: Exception | None = None
    for _ in range(_MAX_PARSE_ATTEMPTS):
        content = await ollama.generate_json(messages)
        try:
            data = json.loads(content)
            recipe = RecipeDto.model_validate(data)
            kcal, p, c, f = _recipe_totals(recipe.ingredients)
            recipe.kcal_per_serving, recipe.protein_g, recipe.carbs_g, recipe.fat_g = kcal, p, c, f
            recipe.anchor = anchor.name
            return recipe
        except (json.JSONDecodeError, ValidationError, TypeError) as exc:
            last_error = exc
    raise RecipeParseError(str(last_error)) from last_error
