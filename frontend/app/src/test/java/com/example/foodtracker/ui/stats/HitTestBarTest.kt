package com.example.foodtracker.ui.stats

import com.example.foodtracker.stats.DayBar
import com.example.foodtracker.stats.DayBarState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HitTestBarTest {
    private val start = LocalDate.of(2026, 10, 26)
    private val dates = (0..6).map { start.plusDays(it.toLong()) }

    @Test fun tapMapsToSlotAndSkipsMissing() {
        val bars = listOf(
            DayBar(dates[0], 1800, 2000, DayBarState.IN_TARGET),
            DayBar(dates[1], 0, null, DayBarState.MISSING),
        )
        assertEquals(dates[0], hitTestBar(10f, 700f, dates, bars))
        assertNull(hitTestBar(150f, 700f, dates, bars))
        assertNull(hitTestBar(650f, 700f, dates, bars))
    }

    @Test fun usesCurrentDatesAfterNavigation() {
        val prev = dates.map { it.minusDays(7) }
        val bars = listOf(DayBar(prev[1], 500, null, DayBarState.UNCONFIRMED))
        assertEquals(prev[1], hitTestBar(150f, 700f, prev, bars))
        assertNull(hitTestBar(150f, 700f, dates, bars))
    }
}
