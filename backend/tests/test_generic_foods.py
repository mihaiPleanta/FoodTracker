from services.generic_foods import GenericFood, GenericFoodsClient


def _client() -> GenericFoodsClient:
    return GenericFoodsClient([
        GenericFood(
            id="usda-chicken-breast-raw", name_ro="Piept de pui crud",
            aliases=["piept de pui", "pui"], categories=["meats", "poultries"],
            kcal_100g=120, protein_100g=22.5, carbs_100g=0, fat_100g=2.6,
        ),
        GenericFood(
            id="usda-rice-white-cooked", name_ro="Orez alb fiert",
            aliases=["orez", "orez alb"], categories=["cereals", "rices"],
            kcal_100g=130, protein_100g=2.7, carbs_100g=28, fat_100g=0.3,
        ),
        GenericFood(
            id="usda-rice-brown-cooked", name_ro="Orez brun fiert",
            aliases=["orez", "orez brun"], categories=["cereals", "rices"],
            kcal_100g=123, protein_100g=2.7, carbs_100g=26, fat_100g=1.0,
        ),
    ])


def test_exact_name_match_is_first():
    res = _client().search("piept de pui", 20)
    assert res[0].name == "Piept de pui crud"


def test_alias_match():
    res = _client().search("pui", 20)
    assert any(r.name == "Piept de pui crud" for r in res)


def test_no_match_returns_empty():
    assert _client().search("xyzzy", 20) == []


def test_to_dto_has_synthetic_barcode_and_no_brand_or_image():
    item = _client().search("orez alb", 20)[0]
    assert item.barcode == "usda-rice-white-cooked"
    assert item.brand is None
    assert item.image_url is None
    assert item.categories == ["cereals", "rices"]


def test_limit_is_respected():
    res = _client().search("orez", 1)  # two foods share alias "orez"
    assert len(res) == 1


def test_multiple_matches_returned_without_limit():
    res = _client().search("orez", 20)
    names = {r.name for r in res}
    assert names == {"Orez alb fiert", "Orez brun fiert"}


def test_token_subset_query_is_included():
    # "piept crud": both tokens are in "Piept de pui crud" → bucket 4 (== threshold), kept.
    res = _client().search("piept crud", 20)
    assert any(r.name == "Piept de pui crud" for r in res)


def test_token_overlap_query_is_excluded():
    # "orez bun": only "orez" overlaps the rice names → bucket 5 (> threshold), dropped.
    assert _client().search("orez bun", 20) == []


def test_macros_are_floats_after_loading_dataset():
    # JSON integers (e.g. kcal 130) must be coerced to float in the loaded dataclass,
    # not just in to_dto(), so direct field access is type-correct.
    from services.generic_foods import _load_dataset
    foods = _load_dataset()
    assert all(isinstance(f.kcal_100g, float) for f in foods)


def test_default_client_loads_bundled_dataset():
    # Smoke test against the real data file.
    from services.generic_foods import get_generic_client
    res = get_generic_client().search("piept de pui", 20)
    assert any("pui" in r.name.lower() for r in res)
