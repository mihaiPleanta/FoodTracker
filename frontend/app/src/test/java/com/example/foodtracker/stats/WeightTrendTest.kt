package com.example.foodtracker.stats

import com.example.foodtracker.model.StatsWeightDto
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeightTrendTest {
    private fun w(date: String, kg: Float) = StatsWeightDto(date, kg)
    private val week = StatsPeriod(PeriodKind.WEEK, LocalDate.parse("2026-11-02"))

    @Test fun firstPointSeedsTrend() {
        val p = WeightTrend.compute(listOf(w("2026-11-01", 80f)))
        assertEquals(80f, p[0].trendKg, 0.0001f)
    }

    @Test fun emaStep() {
        val p = WeightTrend.compute(listOf(w("2026-11-01", 80f), w("2026-11-02", 81f)))
        assertEquals(80.1f, p[1].trendKg, 0.0001f)
    }

    @Test fun unsortedInput_isSorted() {
        val p = WeightTrend.compute(listOf(w("2026-11-02", 81f), w("2026-11-01", 80f)))
        assertEquals(LocalDate.parse("2026-11-01"), p[0].date)
        assertEquals(80.1f, p[1].trendKg, 0.0001f)
    }

    @Test fun periodChange_usesLastPointBeforePeriod() {
        val p = WeightTrend.compute(
            listOf(w("2026-10-20", 80f), w("2026-10-30", 82f), w("2026-11-03", 81f), w("2026-11-05", 81f)),
        )
        val expected = p[3].trendKg - p[1].trendKg
        assertEquals(expected, WeightTrend.periodChange(p, week)!!, 0.0001f)
    }

    @Test fun periodChange_nullWithoutPointInPeriod() {
        val p = WeightTrend.compute(listOf(w("2026-10-30", 80f)))
        assertNull(WeightTrend.periodChange(p, week))
    }

    @Test fun periodChange_nullWithoutPointBefore() {
        val p = WeightTrend.compute(listOf(w("2026-11-03", 80f)))
        assertNull(WeightTrend.periodChange(p, week))
    }
}
