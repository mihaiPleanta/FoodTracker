from datetime import date, timedelta

from services.streak import RecoveryOffer, compute_streak, next_milestone

TODAY = date(2026, 11, 4)


def _d(s: str) -> date:
    """'D-M' within 2026, e.g. '28-10'."""
    d, m = s.split("-")
    return date(2026, int(m), int(d))


def _range(a: str, b: str):
    x, y = _d(a), _d(b)
    return {x + timedelta(days=i) for i in range((y - x).days + 1)}


def _run(logged, recovered=None, declined=None, used=False, today=TODAY):
    return compute_streak(logged, recovered or [], declined or set(), used, today)


def _state(res, d):
    return dict(res.last_14_days)[d]


def test_no_logs():
    r = _run(set())
    assert (r.current, r.best, r.total_logged_days) == (0, 0, 0)
    assert r.offer is None
    states = [s for _, s in r.last_14_days]
    assert states == ["missed"] * 13 + ["today_pending"]
    assert len(r.last_14_days) == 14
    assert r.last_14_days[0][0] == TODAY - timedelta(days=13)
    assert r.last_14_days[-1][0] == TODAY


def test_simple_run_today_logged():
    r = _run(_range("1-11", "4-11"))
    assert r.current == 4 and r.today_logged
    assert r.last_14_days[-1][1] == "today_logged"


def test_today_pending_does_not_break():
    r = _run(_range("1-11", "3-11"))
    assert r.current == 3 and not r.today_logged
    assert r.last_14_days[-1][1] == "today_pending"


def test_single_missing_day_is_pause():
    r = _run(_range("28-10", "31-10") | _range("2-11", "3-11"))
    assert r.current == 6
    assert _state(r, _d("1-11")) == "pause"


def test_second_missing_same_week_breaks():
    logged = _range("26-10", "4-11") - {_d("27-10"), _d("29-10")}
    r = _run(logged)
    assert r.current == 6


def test_two_consecutive_missing_across_weeks_break():
    r = _run(_range("25-10", "31-10") | _range("3-11", "4-11"))
    assert r.current == 2 and r.best == 7


def test_pause_resets_on_monday():
    logged = _range("20-10", "4-11") - {_d("28-10"), _d("3-11")}
    r = _run(logged)
    assert r.current == 14
    assert r.pause_used_on == _d("3-11")


def test_pause_not_used_when_run_zero():
    r = _run(_range("3-11", "4-11"))
    assert r.pause_used_on is None


def test_recovery_offer_basic():
    r = _run(_range("20-10", "1-11"))
    assert r.current == 0
    assert r.offer == RecoveryOffer(13, _d("2-11"), _d("3-11"))


def test_recovery_offer_with_today_logged():
    r = _run(_range("20-10", "1-11") | {_d("4-11")})
    assert r.current == 1
    assert r.offer is not None


def test_no_offer_gap_of_four():
    r = _run(_range("20-10", "30-10"))
    assert r.offer is None


def test_offer_gap_includes_pause_day():
    r = _run(_range("20-10", "31-10"))
    assert r.offer.gap_start == _d("1-11")
    assert r.offer.gap_end == _d("3-11")


def test_no_offer_previous_streak_one():
    r = _run({_d("1-11")})
    assert r.offer is None


def test_no_offer_when_used_this_month():
    assert _run(_range("20-10", "1-11"), used=True).offer is None


def test_no_offer_when_declined():
    r = _run(_range("20-10", "1-11"), declined={_d("2-11")})
    assert r.offer is None


def test_declined_ignored_after_gap_moves():
    r = _run(_range("20-10", "2-11"), declined={_d("2-11")})
    assert r.current == 14 and r.offer is None
    r = _run(_range("20-10", "2-11"), declined={_d("2-11")}, today=_d("5-11"))
    assert r.offer.gap_start == _d("3-11")


def test_recovered_range_restores_streak():
    logged = _range("20-10", "1-11") | {_d("4-11")}
    r = _run(logged, recovered=[(_d("2-11"), _d("3-11"))])
    assert r.current == 14
    assert _state(r, _d("2-11")) == "recovered"
    assert _state(r, _d("3-11")) == "recovered"
    assert r.offer is None


def test_recovery_then_pause_next_day():
    logged = _range("20-10", "1-11") | {_d("5-11")}
    r = _run(logged, recovered=[(_d("2-11"), _d("3-11"))], today=_d("5-11"))
    assert r.current == 14
    assert _state(r, _d("4-11")) == "pause"


def test_recovered_does_not_consume_week_pause():
    r = _run(_range("20-10", "1-11"), recovered=[(_d("2-11"), _d("3-11"))])
    assert r.pause_used_on is None


def test_retroactive_log_counts():
    base = _range("20-10", "1-11")
    assert _run(base).current == 0
    after = _run(base | {_d("3-11")})  # yesterday logged afterwards
    assert after.current == 14
    assert _state(after, _d("3-11")) == "logged"


def test_best_over_history():
    logged = _range("1-9", "20-9") | _range("2-11", "4-11")
    r = _run(logged)
    assert r.best == 20 and r.current == 3 and r.total_logged_days == 23


def test_next_milestone():
    assert [next_milestone(n) for n in (0, 3, 13, 365, 400, 999)] == [3, 7, 14, 400, 500, 1000]
