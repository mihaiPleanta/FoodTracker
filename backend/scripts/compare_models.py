from __future__ import annotations

import json
import time
from dataclasses import dataclass

from pydantic import ValidationError

from schemas import RecipeDto


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
