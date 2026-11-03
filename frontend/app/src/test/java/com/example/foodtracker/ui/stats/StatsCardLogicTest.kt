package com.example.foodtracker.ui.stats

import com.example.foodtracker.model.TopFoodStatDto
import com.example.foodtracker.stats.DayBarState
import com.example.foodtracker.stats.PeriodKind
import com.example.foodtracker.stats.StatsPeriod
import com.example.foodtracker.stats.TrendPoint
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class StatsCardLogicTest {
    private fun month(y: Int, m: Int) = StatsPeriod(PeriodKind.MONTH, LocalDate.of(y, m, 1))

    @Test fun november2026_startsSunday_sixBlanks() {
        assertEquals(6, calendarLeadingBlanks(LocalDate.of(2026, 11, 1)))
        val cells = calendarCells(month(2026, 11))
        assertEquals(6, cells.takeWhile { it == null }.size)
        assertEquals(LocalDate.of(2026, 11, 1), cells[6])
        assertEquals(42, cells.size) // 6 + 30 = 36 -> padded to 6 weeks
    }

    @Test fun february2027_startsMonday_noBlanks_28days() {
        assertEquals(0, calendarLeadingBlanks(LocalDate.of(2027, 2, 1)))
        val cells = calendarCells(month(2027, 2))
        assertEquals(28, cells.size)
        assertEquals(28, cells.count { it != null })
        assertEquals(LocalDate.of(2027, 2, 1), cells.first())
    }

    @Test fun cellsAlwaysFullWeeks() {
        for (m in 1..12) assertEquals(0, calendarCells(month(2026, m)).size % 7)
    }

    @Test fun pointsInPeriod_filtersWarmupAndLater() {
        val p = month(2026, 11)
        fun pt(d: String) = TrendPoint(LocalDate.parse(d), 80f, 80f)
        val r = pointsInPeriod(listOf(pt("2026-10-31"), pt("2026-11-01"), pt("2026-11-30"), pt("2026-12-01")), p)
        assertEquals(listOf(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)), r.map { it.date })
    }

    @Test fun dayStateColor_mapping() {
        val g = Color.Green; val b = Color.Blue; val x = Color.Gray
        assertEquals(g, dayStateColor(DayBarState.IN_TARGET, g, b, x))
        assertEquals(b, dayStateColor(DayBarState.OFF_TARGET, g, b, x))
        assertEquals(x, dayStateColor(DayBarState.UNCONFIRMED, g, b, x))
        assertNull(dayStateColor(DayBarState.MISSING, g, b, x))
    }

    @Test fun formatting() {
        val f = TopFoodStatDto("k", "Ovăz", null, 1250, 83.6f, 4)
        assertEquals("84 g", topFoodTotal(f, true, Locale.US))
        assertTrue(topFoodTotal(f, false, Locale.US).startsWith("1,250"))
        assertEquals("2,1", formatLitres(2.12f, Locale("ro")))
        assertEquals("k", f.toThumbnailItem().barcode)
    }
}
