import json
from datetime import date, timedelta

from services.portions import (
    DEFAULT_PRESETS,
    STANDARD_UNITS,
    PortionTable,
    ProductPortionInfo,
    UnitOption,
    compose_presets,
    label_units,
    load_table,
    merge_units,
)

TODAY = date(2026, 10, 22)


def _table(tmp_path):
    data = {
        "categories": {
            "spreads": {"units": [{"unit": "tbsp", "grams": 15}], "presets": [10, 20]},
            "honeys": {"units": [{"unit": "tsp", "grams": 7}, {"unit": "tbsp", "grams": 21}], "presets": [10, 15]},
            "sweeteners": {"units": [{"unit": "tsp", "grams": 4}], "presets": [5]},
        },
        "generics": {"usda-honey": {"units": [{"unit": "tsp", "grams": 7}], "presets": [10]}},
    }
    p = tmp_path / "t.json"
    p.write_text(json.dumps(data), encoding="utf-8")
    return load_table(p)


def test_lookup_most_specific_tag_wins(tmp_path):
    units, presets = _table(tmp_path).lookup(["spreads", "sweet-spreads", "honeys"], None)
    assert [(u.unit, u.grams, u.source) for u in units] == [("tsp", 7, "estimate"), ("tbsp", 21, "estimate")]
    assert presets == [10, 15]


def test_lookup_generic_id_beats_category(tmp_path):
    units, presets = _table(tmp_path).lookup(["sweeteners"], "usda-honey")
    assert [(u.unit, u.grams) for u in units] == [("tsp", 7)]
    assert presets == [10]


def test_lookup_no_match_empty(tmp_path):
    assert _table(tmp_path).lookup(["unknown"], "nope") == ([], [])


def test_label_units_filters():
    def info(s, p):
        return ProductPortionInfo(categories=[], serving_g=s, package_g=p)

    for s in (None, 0, 100, 900, 1000):
        assert [u.unit for u in label_units(info(s, 900))] == ["package"]
    assert label_units(info(30, 900)) == [
        UnitOption("serving", 30, "label"),
        UnitOption("package", 900, "label"),
    ]
    assert [u.unit for u in label_units(info(30, None))] == ["serving"]


def test_compose_presets_history_first():
    h = [(180, TODAY), (180, TODAY - timedelta(1)), (120, TODAY - timedelta(2)),
         (120, TODAY - timedelta(3)), (90, TODAY - timedelta(4))]
    assert compose_presets(h, [100, 150, 200, 250, 300], None, TODAY) == [180, 120, 100, 150, 200]


def test_compose_presets_ignores_old_history():
    old = TODAY - timedelta(91)
    h = [(180, old), (180, old), (180, old)]
    assert compose_presets(h, [100, 150, 200, 250, 300], None, TODAY) == [100, 150, 200, 250, 300]
    edge = TODAY - timedelta(90)
    assert compose_presets([(77, edge), (77, edge)], [100, 150, 200, 250, 300], None, TODAY)[0] == 77


def test_compose_presets_dedup_and_default():
    h = [(100, TODAY), (100, TODAY)]
    assert compose_presets(h, [100, 150, 200, 250, 300], None, TODAY) == [100, 150, 200, 250, 300]
    assert compose_presets([], [], None, TODAY) == DEFAULT_PRESETS


def test_compose_presets_custom_overrides():
    h = [(180, TODAY), (180, TODAY)]
    assert compose_presets(h, [100], [42, 84], TODAY) == [42, 84]


def test_merge_user_correction_in_place():
    label = [UnitOption("package", 500, "label")]
    est = [UnitOption("tsp", 7, "estimate"), UnitOption("tbsp", 21, "estimate")]
    out = merge_units(label, est, [("tsp", 6), ("bolul meu", 250)])
    assert [(u.unit, u.grams, u.source) for u in out] == [
        ("package", 500, "label"), ("tsp", 6, "user"), ("tbsp", 21, "estimate"), ("bolul meu", 250, "user"),
    ]


def test_real_table_loads():
    t = load_table()
    assert isinstance(t, PortionTable)
    raw = json.loads((__import__("pathlib").Path(__file__).resolve().parent.parent / "data" / "portion_units.json").read_text(encoding="utf-8"))
    for section in ("categories", "generics"):
        for entry in raw[section].values():
            assert len(entry.get("presets", [])) <= 5
            for u in entry.get("units", []):
                assert u["unit"] in STANDARD_UNITS and u["grams"] > 0
    units, _ = t.lookup(["honeys"], None)
    assert [(u.unit, u.grams) for u in units] == [("tsp", 7), ("tbsp", 21)]
    egg, _ = t.lookup(["eggs"], "usda-egg-whole-raw")
    assert {u.unit: u.grams for u in egg} == {"piece_small": 38, "piece_medium": 44, "piece_large": 50}


def test_label_units_volume_package_is_estimate():
    vol = ProductPortionInfo(categories=[], serving_g=None, package_g=1000, package_is_volume=True)
    assert label_units(vol) == [UnitOption("package", 1000, "estimate")]
    mass = ProductPortionInfo(categories=[], serving_g=None, package_g=1000)
    assert label_units(mass) == [UnitOption("package", 1000, "label")]


def test_real_table_dry_rice_pasta_vs_cooked_generics():
    t = load_table()
    for cat, serving in (("rices", 75), ("pastas", 80)):
        units, presets = t.lookup([cat], None)
        assert {u.unit: u.grams for u in units} == {"serving": serving}
        assert presets == [50, 75, 100, 125, 150]
    for gid, cats, bowl in (
        ("usda-rice-white-cooked", ["cereals", "rices"], 180),
        ("usda-rice-brown-cooked", ["cereals", "rices"], 180),
        ("usda-pasta-cooked", ["pastas"], 200),
    ):
        units, _ = t.lookup(cats, gid)
        assert {u.unit: u.grams for u in units}.get("bowl") == bowl
