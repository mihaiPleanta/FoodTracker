package com.example.foodtracker.stats

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsPeriodTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test fun week_startsMonday() {
        val p = StatsPeriod.containing(PeriodKind.WEEK, d("2026-11-05"))
        assertEquals(d("2026-11-02"), p.start)
        assertEquals(d("2026-11-08"), p.end)
        assertEquals(7, p.days().size)
    }

    @Test fun month_bounds() {
        val p = StatsPeriod.containing(PeriodKind.MONTH, d("2028-02-10"))
        assertEquals(d("2028-02-01"), p.start)
        assertEquals(d("2028-02-29"), p.end)
        assertEquals(29, p.days().size)
    }

    @Test fun previous_next_roundTrip() {
        for (kind in PeriodKind.values()) {
            val p = StatsPeriod.containing(kind, d("2026-03-31"))
            assertEquals(p, p.next().previous())
            assertEquals(p, p.previous().next())
        }
        val m = StatsPeriod.containing(PeriodKind.MONTH, d("2026-03-31"))
        assertEquals(d("2026-02-01"), m.previous().start)
        assertEquals(d("2026-02-28"), m.previous().end)
    }

    @Test fun elapsedDays_currentWeek() {
        val p = StatsPeriod.containing(PeriodKind.WEEK, d("2026-11-04"))
        assertEquals(3, p.elapsedDays(d("2026-11-04"))) // Wed
        assertEquals(true, p.isCurrent(d("2026-11-04")))
    }

    @Test fun elapsedDays_pastPeriod_isFullLength() {
        val p = StatsPeriod.containing(PeriodKind.WEEK, d("2026-10-28"))
        assertEquals(7, p.elapsedDays(d("2026-11-04")))
        assertEquals(false, p.isCurrent(d("2026-11-04")))
    }

    @Test fun elapsedDays_futurePeriod_isZero() {
        val p = StatsPeriod.containing(PeriodKind.MONTH, d("2026-12-01"))
        assertEquals(0, p.elapsedDays(d("2026-11-04")))
        assertEquals(false, p.isCurrent(d("2026-11-04")))
    }
}
