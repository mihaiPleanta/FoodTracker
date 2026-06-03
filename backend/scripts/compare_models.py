from __future__ import annotations

import argparse
import asyncio
import json
import statistics
import time
from dataclasses import asdict, dataclass
from datetime import datetime
from pathlib import Path

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


def _fmt(value: float | None, suffix: str = "", nd: int = 1) -> str:
    return "—" if value is None else f"{value:.{nd}f}{suffix}"


def render_markdown(metrics, examples, runs: int, language: str) -> str:
    lines: list[str] = []
    lines.append("# Comparație modele Ollama")
    lines.append("")
    lines.append(
        f"- Repetări/scenariu: **{runs}** · Limbă: **{language}** · "
        f"Scenarii: **{len(SCENARIOS)}**"
    )
    lines.append("")
    lines.append("## Sumar metrici")
    lines.append("")
    lines.append(
        "| Model | JSON valid din prima | Succes ≤3 | Încercări medii | "
        "Latență mediană (s) | Latență p95 (s) | Eroare macro | Pași medii | Ingrediente medii |"
    )
    lines.append("|---|---|---|---|---|---|---|---|---|")
    for m in metrics:
        if m.skipped:
            lines.append(f"| {m.model} | _skip (indisponibil)_ |  |  |  |  |  |  |  |")
            continue
        lines.append(
            f"| {m.model} | {_fmt(m.first_attempt_valid_pct, '%')} | "
            f"{_fmt(m.success_pct, '%')} | {_fmt(m.avg_attempts, nd=2)} | "
            f"{_fmt(m.latency_median, 's', nd=2)} | {_fmt(m.latency_p95, 's', nd=2)} | "
            f"{_fmt(m.macro_consistency_pct, '%')} | {_fmt(m.avg_steps, nd=1)} | "
            f"{_fmt(m.avg_ingredients, nd=1)} |"
        )
    lines.append("")
    lines.append("## Exemple side-by-side")
    for scenario in SCENARIOS:
        lines.append("")
        lines.append(f"### {scenario.label} ({scenario.meal_type})")
        for model, by_label in examples.items():
            recipe = by_label.get(scenario.label)
            lines.append("")
            lines.append(f"**{model}:**")
            if recipe is None:
                lines.append("_nicio rețetă validă_")
                continue
            lines.append(f"- *{recipe.title}* — {recipe.description}")
            lines.append(
                f"- Macros: {recipe.kcal_per_serving} kcal · "
                f"{recipe.protein_g}P / {recipe.carbs_g}C / {recipe.fat_g}G"
            )
            ingr = "; ".join(f"{i.name} ({i.quantity})" for i in recipe.ingredients)
            lines.append(f"- Ingrediente: {ingr}")
            for idx, step in enumerate(recipe.steps, 1):
                lines.append(f"  {idx}. {step}")
    return "\n".join(lines) + "\n"


def _client_factory(model: str) -> OllamaClient:
    return OllamaClient(model=model)


async def _amain(args) -> None:
    models = [m.strip() for m in args.models.split(",") if m.strip()]
    metrics, examples = await run_comparison(
        models, SCENARIOS, args.runs, _client_factory, args.lang
    )
    report = render_markdown(metrics, examples, args.runs, args.lang)
    print(report)

    out_dir = Path(__file__).parent / "out"
    out_dir.mkdir(exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d-%H%M")
    md_path = out_dir / f"compare-{stamp}.md"
    md_path.write_text(report, encoding="utf-8")
    print(f"\n[raport salvat în {md_path}]")

    if args.json:
        raw = {
            "runs": args.runs,
            "language": args.lang,
            "metrics": [asdict(m) for m in metrics],
            "examples": {
                model: {label: r.model_dump() for label, r in by_label.items()}
                for model, by_label in examples.items()
            },
        }
        json_path = out_dir / f"compare-{stamp}.json"
        json_path.write_text(
            json.dumps(raw, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        print(f"[raw JSON salvat în {json_path}]")


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Compară modele Ollama pe promptul de rețete al aplicației."
    )
    parser.add_argument("--models", default="gemma3:4b",
                        help="Modele separate prin virgulă, ex: gemma3:4b,qwen2.5:7b")
    parser.add_argument("--runs", type=int, default=5,
                        help="Repetări per scenariu (default 5)")
    parser.add_argument("--lang", choices=["ro", "en"], default="ro")
    parser.add_argument("--json", action="store_true", help="Scrie și raw JSON")
    args = parser.parse_args()
    asyncio.run(_amain(args))


if __name__ == "__main__":
    main()
