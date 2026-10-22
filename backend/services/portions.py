from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import date, timedelta
from pathlib import Path
from typing import Dict, List, Optional, Tuple

_DATA_PATH = Path(__file__).resolve().parent.parent / "data" / "portion_units.json"

STANDARD_UNITS = frozenset({
    "tsp", "tbsp", "slice", "piece", "piece_small", "piece_medium", "piece_large",
    "serving", "package", "cup", "glass", "bowl", "handful", "ml",
})
DEFAULT_PRESETS = [50, 100, 150, 200, 250]

_PRESET_COUNT = 5
_MAX_HISTORY_PRESETS = 3
_MIN_HISTORY_COUNT = 2
_HISTORY_DAYS = 90


@dataclass(frozen=True)
class UnitOption:
    unit: str
    grams: float
    source: str  # "label" | "estimate" | "user"


@dataclass(frozen=True)
class ProductPortionInfo:
    categories: List[str]  # full list, no "xx:" prefix, general -> specific
    serving_g: Optional[float]
    package_g: Optional[float]
    package_is_volume: bool = False  # package_g converted from ml/l (1 ml ~ 1 g)


@dataclass(frozen=True)
class _Entry:
    units: Tuple[Tuple[str, float], ...]
    presets: Tuple[int, ...]


_DIACRITICS = str.maketrans("ăâîșşțţ", "aaisstt")


def _normalize_tag(tag: str) -> str:
    """'Ulei de măsline' -> 'ulei-de-masline', so RO/free-text OFF tags match table keys."""
    return "-".join(tag.strip().lower().translate(_DIACRITICS).split())


class PortionTable:
    def __init__(
        self,
        categories: Dict[str, _Entry],
        generics: Dict[str, _Entry],
        aliases: Optional[Dict[str, str]] = None,
    ):
        self._categories = categories
        self._generics = generics
        self._aliases = {_normalize_tag(k): v for k, v in (aliases or {}).items()}

    def _entry_for(self, tag: str) -> Optional[_Entry]:
        key = _normalize_tag(tag)
        return self._categories.get(key) or self._categories.get(self._aliases.get(key, ""))

    @staticmethod
    def _result(entry: _Entry) -> Tuple[List[UnitOption], List[int]]:
        return [UnitOption(u, g, "estimate") for u, g in entry.units], list(entry.presets)

    def lookup(self, categories: List[str], generic_id: Optional[str]) -> Tuple[List[UnitOption], List[int]]:
        if generic_id is not None and generic_id in self._generics:
            return self._result(self._generics[generic_id])
        for tag in reversed(categories):
            entry = self._entry_for(tag)
            if entry is not None:
                return self._result(entry)
        return [], []


def _parse_entry(raw: dict) -> _Entry:
    units = tuple((str(u["unit"]), float(u["grams"])) for u in raw.get("units", []))
    presets = tuple(int(p) for p in raw.get("presets", []))
    return _Entry(units, presets)


def load_table(path: Path = _DATA_PATH) -> PortionTable:
    """Load the estimate table. Fails fast on a missing/corrupt file."""
    raw = json.loads(Path(path).read_text(encoding="utf-8"))
    return PortionTable(
        {k: _parse_entry(v) for k, v in raw.get("categories", {}).items()},
        {k: _parse_entry(v) for k, v in raw.get("generics", {}).items()},
        {str(k): str(v) for k, v in raw.get("aliases", {}).items()},
    )


def label_units(info: ProductPortionInfo) -> List[UnitOption]:
    out: List[UnitOption] = []
    s, p = info.serving_g, info.package_g
    if s is not None and s > 0 and s != 100 and (p is None or s < p):
        out.append(UnitOption("serving", s, "label"))
    if p is not None and p > 0:
        out.append(UnitOption("package", p, "estimate" if info.package_is_volume else "label"))
    return out


def compose_presets(
    history_grams: List[Tuple[int, date]],
    category_presets: List[int],
    custom: Optional[List[int]],
    today: date,
) -> List[int]:
    if custom:
        return list(custom)
    cutoff = today - timedelta(days=_HISTORY_DAYS)
    stats: Dict[int, List] = {}  # grams -> [count, most recent date]
    for grams, log_date in history_grams:
        if log_date < cutoff:
            continue
        s = stats.setdefault(grams, [0, log_date])
        s[0] += 1
        if log_date > s[1]:
            s[1] = log_date
    frequent = [g for g, (c, _) in stats.items() if c >= _MIN_HISTORY_COUNT]
    frequent.sort(key=lambda g: (-stats[g][0], -stats[g][1].toordinal()))
    result = frequent[:_MAX_HISTORY_PRESETS]
    base = category_presets if category_presets else DEFAULT_PRESETS
    for g in sorted(base):
        if len(result) >= _PRESET_COUNT:
            break
        if g not in result:
            result.append(g)
    for g in DEFAULT_PRESETS:
        if len(result) >= _PRESET_COUNT:
            break
        if g not in result:
            result.append(g)
    return result


def merge_units(
    label: List[UnitOption],
    estimates: List[UnitOption],
    user: List[Tuple[str, float]],
) -> List[UnitOption]:
    merged: List[UnitOption] = []
    index: Dict[str, int] = {}
    for opt in list(label) + list(estimates):
        if opt.unit in index:
            continue
        index[opt.unit] = len(merged)
        merged.append(opt)
    for unit, grams in user:
        opt = UnitOption(unit, grams, "user")
        if unit in index:
            merged[index[unit]] = opt
        else:
            index[unit] = len(merged)
            merged.append(opt)
    return merged


# Loaded at import so a corrupt file fails fast at startup (like generic_foods).
TABLE = load_table()
