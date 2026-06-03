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
