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


import json

from scripts.compare_models import RunResult, run_single


def _valid_json(**overrides) -> str:
    base = dict(
        title="Omletă",
        description="Omletă cu legume.",
        ingredients=[{"name": "ou", "quantity": "2 buc"}],
        steps=["bate ouăle", "prăjește"],
        servings=1,
        kcal_per_serving=165,
        protein_g=10,
        carbs_g=20,
        fat_g=5,
    )
    base.update(overrides)
    return json.dumps(base)


class FakeClient:
    """Returns queued responses in order. A str is returned as content;
    an Exception instance is raised."""

    def __init__(self, responses):
        self._responses = list(responses)
        self.calls = 0

    async def generate_json(self, messages):
        self.calls += 1
        r = self._responses.pop(0)
        if isinstance(r, Exception):
            raise r
        return r


async def test_run_single_first_attempt_valid():
    client = FakeClient([_valid_json()])
    result = await run_single(client, messages=[])
    assert isinstance(result, RunResult)
    assert result.first_attempt_valid is True
    assert result.attempts_used == 1
    assert result.recipe is not None
    assert len(result.latencies) == 1


async def test_run_single_recovers_on_second_attempt():
    client = FakeClient(["not json", _valid_json()])
    result = await run_single(client, messages=[])
    assert result.first_attempt_valid is False
    assert result.attempts_used == 2
    assert result.recipe is not None
    assert len(result.latencies) == 2


async def test_run_single_all_attempts_fail():
    client = FakeClient(["bad", "bad", "bad"])
    result = await run_single(client, messages=[])
    assert result.first_attempt_valid is False
    assert result.attempts_used is None
    assert result.recipe is None
    assert len(result.latencies) == 3


from scripts.compare_models import ModelMetrics, aggregate


def _mk_result(first_valid, attempts, latencies, recipe):
    return RunResult(first_valid, attempts, latencies, recipe)


async def test_aggregate_basic_rates():
    valid = _recipe(kcal_per_serving=165, protein_g=10, carbs_g=20, fat_g=5)
    results = [
        _mk_result(True, 1, [0.5], valid),
        _mk_result(False, 2, [0.4, 0.6], valid),
        _mk_result(False, None, [0.3, 0.3, 0.3], None),
    ]
    m = aggregate("gemma3:4b", results)
    assert isinstance(m, ModelMetrics)
    assert m.total_runs == 3
    assert abs(m.first_attempt_valid_pct - (100 / 3)) < 1e-6
    assert abs(m.success_pct - (200 / 3)) < 1e-6
    assert m.avg_attempts == 1.5  # mean of [1, 2]
    # macro_consistency is 0 for both valid recipes
    assert m.macro_consistency_pct == 0.0
    assert m.avg_steps == 2.0          # _recipe has 2 steps
    assert m.avg_ingredients == 1.0    # _recipe has 1 ingredient
    assert m.skipped is False


async def test_aggregate_empty_is_skipped():
    m = aggregate("missing:model", [])
    assert m.skipped is True
    assert m.total_runs == 0
    assert m.macro_consistency_pct is None
    assert m.avg_attempts is None


from services.ollama_client import OllamaUnavailable

from scripts.compare_models import SCENARIOS, run_comparison


async def test_scenarios_cover_four_meals():
    assert len(SCENARIOS) == 4
    assert {s.meal_type for s in SCENARIOS} == {"BREAKFAST", "LUNCH", "DINNER", "SNACKS"}


async def test_run_comparison_collects_metrics_and_examples():
    # One model, always-valid client. 1 run per scenario over 4 scenarios = 4 runs.
    def factory(model):
        return FakeClient([_valid_json()] * len(SCENARIOS))

    metrics, examples = await run_comparison(
        ["gemma3:4b"], SCENARIOS, runs=1, client_factory=factory
    )
    assert len(metrics) == 1
    assert metrics[0].total_runs == len(SCENARIOS)
    assert metrics[0].skipped is False
    # one example recipe per scenario for the model
    assert len(examples["gemma3:4b"]) == len(SCENARIOS)


async def test_run_comparison_marks_unavailable_model_skipped():
    def factory(model):
        return FakeClient([OllamaUnavailable("connection refused")])

    metrics, examples = await run_comparison(
        ["ghost:model"], SCENARIOS, runs=1, client_factory=factory
    )
    assert metrics[0].skipped is True
    assert examples["ghost:model"] == {}


from scripts.compare_models import render_markdown


async def test_render_markdown_has_table_and_examples():
    valid = _recipe(title="Omletă cu pui")
    metrics = [
        ModelMetrics(
            model="gemma3:4b", total_runs=4,
            first_attempt_valid_pct=75.0, success_pct=100.0, avg_attempts=1.25,
            latency_mean=0.5, latency_median=0.5, latency_p95=0.6,
            macro_consistency_pct=12.0, avg_steps=3.0, avg_ingredients=4.0,
        ),
        ModelMetrics(
            model="ghost:model", total_runs=0,
            first_attempt_valid_pct=0.0, success_pct=0.0, avg_attempts=None,
            latency_mean=None, latency_median=None, latency_p95=None,
            macro_consistency_pct=None, avg_steps=None, avg_ingredients=None,
            skipped=True,
        ),
    ]
    examples = {"gemma3:4b": {"Banană": valid}, "ghost:model": {}}
    md = render_markdown(metrics, examples, runs=1, language="ro")
    assert "## Sumar metrici" in md
    assert "gemma3:4b" in md
    assert "Omletă cu pui" in md          # example recipe rendered
    assert "skip" in md.lower()            # skipped model noted
    assert "## Exemple side-by-side" in md
