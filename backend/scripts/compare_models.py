from __future__ import annotations

import json
import statistics
import time
from dataclasses import dataclass

from pydantic import ValidationError

from schemas import RecipeDto, TopFood
from services.nutrition import Goals
from services.ollama_client import OllamaClient, OllamaUnavailable
from services.recipes import build_recipe_prompt


@dataclass
class RunResult:
    first_attempt_valid: bool
    attempts_used: int | None  # 1..max_attempts, or None if every attempt failed
    latencies: list[float]
    recipe: RecipeDto | None


def _parse(content: str) -> RecipeDto:
    return RecipeDto.model_validate(json.loads(content))


async def run_single(client, messages, max_attempts: int = 3) -> RunResult:
    """Mirror the production retry loop: up to max_attempts, timing each call.
    `client` only needs an async `generate_json(messages) -> str`."""
    latencies: list[float] = []
    for attempt in range(1, max_attempts + 1):
        start = time.perf_counter()
        content = await client.generate_json(messages)
        latencies.append(time.perf_counter() - start)
        try:
            recipe = _parse(content)
        except (json.JSONDecodeError, ValidationError, TypeError):
            continue
        return RunResult(attempt == 1, attempt, latencies, recipe)
    return RunResult(False, None, latencies, None)


def macro_consistency(recipe: RecipeDto) -> float | None:
    """Relative error between declared kcal and macro-derived kcal
    (4*protein + 4*carbs + 9*fat). None when declared kcal <= 0."""
    if recipe.kcal_per_serving <= 0:
        return None
    derived = 4 * recipe.protein_g + 4 * recipe.carbs_g + 9 * recipe.fat_g
    return abs(recipe.kcal_per_serving - derived) / recipe.kcal_per_serving


@dataclass
class ModelMetrics:
    model: str
    total_runs: int
    first_attempt_valid_pct: float
    success_pct: float
    avg_attempts: float | None
    latency_mean: float | None
    latency_median: float | None
    latency_p95: float | None
    macro_consistency_pct: float | None
    avg_steps: float | None
    avg_ingredients: float | None
    skipped: bool = False


def _p95(values: list[float]) -> float | None:
    if not values:
        return None
    s = sorted(values)
    idx = min(len(s) - 1, int(round(0.95 * (len(s) - 1))))
    return s[idx]


def aggregate(model: str, results: list[RunResult]) -> ModelMetrics:
    total = len(results)
    if total == 0:
        return ModelMetrics(
            model, 0, 0.0, 0.0, None, None, None, None, None, None, None,
            skipped=True,
        )
    first = sum(1 for r in results if r.first_attempt_valid)
    success = [r for r in results if r.attempts_used is not None]
    all_lat = [lat for r in results for lat in r.latencies]
    valid_recipes = [r.recipe for r in success if r.recipe is not None]
    macro_vals = [
        mc for r in valid_recipes if (mc := macro_consistency(r)) is not None
    ]
    return ModelMetrics(
        model=model,
        total_runs=total,
        first_attempt_valid_pct=100 * first / total,
        success_pct=100 * len(success) / total,
        avg_attempts=statistics.mean(r.attempts_used for r in success) if success else None,
        latency_mean=statistics.mean(all_lat) if all_lat else None,
        latency_median=statistics.median(all_lat) if all_lat else None,
        latency_p95=_p95(all_lat),
        macro_consistency_pct=100 * statistics.mean(macro_vals) if macro_vals else None,
        avg_steps=statistics.mean(len(r.steps) for r in valid_recipes) if valid_recipes else None,
        avg_ingredients=statistics.mean(len(r.ingredients) for r in valid_recipes) if valid_recipes else None,
    )


@dataclass
class Scenario:
    label: str
    anchor: TopFood
    meal_type: str


DEFAULT_GOALS = Goals(
    calorie_goal=1900,
    protein_goal_g=120,
    carbs_goal_g=180,
    fat_goal_g=53,
    mode="DEFICIT",
)

SCENARIOS: list[Scenario] = [
    Scenario(
        "Banană",
        TopFood(name="Banană", brand=None, kcal_100g=89, protein_100g=1.1, carbs_100g=23, fat_100g=0.3),
        "BREAKFAST",
    ),
    Scenario(
        "Piept de pui",
        TopFood(name="Piept de pui", brand=None, kcal_100g=165, protein_100g=31, carbs_100g=0, fat_100g=3.6),
        "LUNCH",
    ),
    Scenario(
        "Orez",
        TopFood(name="Orez", brand=None, kcal_100g=130, protein_100g=2.7, carbs_100g=28, fat_100g=0.3),
        "DINNER",
    ),
    Scenario(
        "Iaurt grecesc",
        TopFood(name="Iaurt grecesc", brand=None, kcal_100g=59, protein_100g=10, carbs_100g=3.6, fat_100g=0.4),
        "SNACKS",
    ),
]


async def run_comparison(models, scenarios, runs, client_factory, language: str = "ro"):
    """For each model, run every scenario `runs` times. Returns
    (metrics: list[ModelMetrics], examples: dict[model -> dict[label -> RecipeDto]]).
    A model whose first call raises OllamaUnavailable is recorded as skipped."""
    metrics: list[ModelMetrics] = []
    examples: dict[str, dict[str, RecipeDto]] = {}
    for model in models:
        client = client_factory(model)
        all_results: list[RunResult] = []
        model_examples: dict[str, RecipeDto] = {}
        unavailable = False
        for scenario in scenarios:
            messages = build_recipe_prompt(
                scenario.anchor, scenario.meal_type, DEFAULT_GOALS, language
            )
            for _ in range(runs):
                try:
                    result = await run_single(client, messages)
                except OllamaUnavailable:
                    unavailable = True
                    break
                all_results.append(result)
                if scenario.label not in model_examples and result.recipe is not None:
                    model_examples[scenario.label] = result.recipe
            if unavailable:
                break
        examples[model] = {} if unavailable else model_examples
        metrics.append(
            aggregate(model, []) if unavailable else aggregate(model, all_results)
        )
    return metrics, examples
