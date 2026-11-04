"""Pure streak computation (no I/O). See the streaks design spec."""
from dataclasses import dataclass
from datetime import date, timedelta
from typing import Dict, List, Literal, Optional, Set, Tuple

MILESTONES: tuple = (3, 7, 14, 30, 50, 100, 180, 365)
DayState = Literal["logged", "pause", "recovered", "missed", "today_pending", "today_logged"]

MAX_GAP_DAYS = 3
MIN_PREVIOUS_STREAK = 2


@dataclass(frozen=True)
class RecoveryOffer:
    previous_streak: int
    gap_start: date
    gap_end: date


@dataclass(frozen=True)
class StreakResult:
    current: int
    best: int
    total_logged_days: int
    today_logged: bool
    pause_used_on: Optional[date]
    last_14_days: List[Tuple[date, str]]
    offer: Optional[RecoveryOffer]


def _iso_week(d: date) -> Tuple[int, int]:
    iso = d.isocalendar()
    return (iso[0], iso[1])


def compute_streak(
    logged: Set[date],
    recovered_ranges: List[Tuple[date, date]],
    declined_gap_starts: Set[date],
    recovery_used_this_month: bool,
    today: date,
) -> StreakResult:
    logged = {d for d in logged if d <= today}

    def is_recovered(d: date) -> bool:
        return any(s <= d <= e for s, e in recovered_ranges)

    states: Dict[date, str] = {}
    run_at: Dict[date, int] = {}
    run = 0
    best = 0
    pause_weeks: Dict[Tuple[int, int], date] = {}
    prev_neutral = False

    if logged:
        d = min(logged)
        while d < today:
            if d in logged:
                run += 1
                prev_neutral = False
                states[d] = "logged"
            elif is_recovered(d):
                states[d] = "recovered"
            elif run > 0 and _iso_week(d) not in pause_weeks and not prev_neutral:
                pause_weeks[_iso_week(d)] = d
                prev_neutral = True
                states[d] = "pause"
            else:
                run = 0
                prev_neutral = False
                states[d] = "missed"
            best = max(best, run)
            run_at[d] = run
            d += timedelta(days=1)

    today_logged = today in logged
    if today_logged:
        run += 1
        best = max(best, run)
    states[today] = "today_logged" if today_logged else "today_pending"

    last_14 = [
        (d, states.get(d, "missed"))
        for d in (today - timedelta(days=i) for i in range(13, -1, -1))
    ]
    pause_used_on = pause_weeks.get(_iso_week(today))

    return StreakResult(
        current=run,
        best=best,
        total_logged_days=len(logged),
        today_logged=today_logged,
        pause_used_on=pause_used_on,
        last_14_days=last_14,
        offer=_compute_offer(
            logged, states, run_at, declined_gap_starts, recovery_used_this_month, today
        ),
    )


def _compute_offer(
    logged: Set[date],
    states: Dict[date, str],
    run_at: Dict[date, int],
    declined_gap_starts: Set[date],
    recovery_used_this_month: bool,
    today: date,
) -> Optional[RecoveryOffer]:
    if recovery_used_this_month:
        return None
    before_today = [d for d in logged if d < today]
    if not before_today:
        return None
    last = max(before_today)
    gap_start = last + timedelta(days=1)
    gap_end = today - timedelta(days=1)
    if gap_end < gap_start:
        return None
    if (gap_end - gap_start).days + 1 > MAX_GAP_DAYS:
        return None
    if run_at.get(gap_end, 0) != 0:
        return None
    if any(states[gap_start + timedelta(days=i)] == "recovered"
           for i in range((gap_end - gap_start).days + 1)):
        return None
    if run_at[last] < MIN_PREVIOUS_STREAK:
        return None
    if gap_start in declined_gap_starts:
        return None
    return RecoveryOffer(run_at[last], gap_start, gap_end)


def next_milestone(current: int) -> int:
    for m in MILESTONES:
        if m > current:
            return m
    return (current // 100 + 1) * 100
