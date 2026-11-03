package com.example.foodtracker.stats

import com.example.foodtracker.model.StatsDayDto
import com.example.foodtracker.model.StatsGoalsDto
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PeriodStatsCalculatorTest {
    private val week = StatsPeriod.containing(PeriodKind.WEEK, LocalDate.parse("2026-11-02")) // Mon 2 - Sun 8
    private val today = LocalDate.parse("2026-11-08")

    private fun goals(cal: Int = 2000, tdee: Int = 2500, p: Int = 100, c: Int = 200, f: Int = 60) =
        StatsGoalsDto(cal, p, c, f, tdee)

    private fun day(
        date: String, kcal: Int, status: String? = "complete", hasFood: Boolean = true,
        goals: StatsGoalsDto? = if (status == "complete") goals() else null,
        p: Float = 0f, c: Float = 0f, f: Float = 0f, water: Float = 0f,
    ) = StatsDayDto(date, kcal, p, c, f, hasFood, status, goals, water)

    private fun sum(period: StatsPeriod, days: List<StatsDayDto>, tol: Int = 10, now: LocalDate = today, water: Float = 2f) =
        PeriodStatsCalculator.summarize(period, days, tol, water, now)

    @Test fun onlyCompleteDaysWithFoodCount() {
        val s = sum(week, listOf(
            day("2026-11-02", 2000),
            day("2026-11-03", 1000, status = "incomplete"),
            day("2026-11-04", 500, status = null),
        ))
        assertEquals(1, s.completeDays)
        assertEquals(2000, s.avgKcal)
        assertEquals(7, s.elapsedDays)
    }

    @Test fun completeDayWithNoKcal_isIgnored() {
        val s = sum(week, listOf(
            day("2026-11-02", 2000),
            day("2026-11-03", 0, hasFood = false, water = 3f),
        ))
        assertEquals(1, s.completeDays)
        assertEquals(DayBarState.MISSING, s.bars.first { it.date == LocalDate.parse("2026-11-03") }.state)
        assertEquals(2f, s.avgHydrationL!!, 0.001f)
    }

    @Test fun inTarget_usesTolerance() {
        fun inT(kcal: Int) = sum(week, listOf(day("2026-11-02", kcal))).inTargetDays
        assertEquals(1, inT(2200))
        assertEquals(0, inT(2201))
        assertEquals(1, inT(1800))
        assertEquals(0, inT(1799))
    }

    @Test fun usesPerDaySnapshotGoal() {
        val s = sum(week, listOf(
            day("2026-11-02", 2400, goals = goals(cal = 2000)),
            day("2026-11-03", 2400, goals = goals(cal = 2500)),
        ))
        assertEquals(1, s.inTargetDays)
        assertEquals(DayBarState.OFF_TARGET, s.bars[0].state)
        assertEquals(DayBarState.IN_TARGET, s.bars[1].state)
        assertEquals(2000, s.bars[0].goalKcal)
        assertEquals(2500, s.bars[1].goalKcal)
    }

    @Test fun balanceVsPlan_isSumOverCompleteDays() {
        val s = sum(week, listOf(
            day("2026-11-02", 2300),
            day("2026-11-03", 1700),
            day("2026-11-04", 2100),
            day("2026-11-05", 5000, status = "incomplete"),
        ))
        assertEquals(300 - 300 + 100, s.balanceVsPlanKcal)
    }

    @Test fun estimatedKg_isBalanceVsTdeeOver7700() {
        val s = sum(week, listOf(day("2026-11-02", 2500 - 3850)))
        assertEquals(-3850, s.balanceVsTdeeKcal)
        assertEquals(-0.5f, s.estimatedKgChange!!, 0.0001f)
    }

    @Test fun macros_averageOfDaysAndOfSnapshotGoals() {
        val s = sum(week, listOf(
            day("2026-11-02", 2000, p = 100f, c = 200f, f = 50f, goals = goals(p = 100, c = 200, f = 60)),
            day("2026-11-03", 2000, p = 140f, c = 240f, f = 70f, goals = goals(p = 120, c = 220, f = 80)),
            day("2026-11-04", 2000, p = 999f, status = "incomplete"),
        ))
        val m = s.macros!!
        assertEquals(120f, m.proteinG, 0.001f)
        assertEquals(220f, m.carbsG, 0.001f)
        assertEquals(60f, m.fatG, 0.001f)
        assertEquals(110f, m.proteinGoalG, 0.001f)
        assertEquals(210f, m.carbsGoalG, 0.001f)
        assertEquals(70f, m.fatGoalG, 0.001f)
    }

    @Test fun hydration_onlyFromCompleteDays_andGoalDays() {
        val s = sum(week, listOf(
            day("2026-11-02", 2000, water = 2.5f),
            day("2026-11-03", 2000, water = 1.5f),
            day("2026-11-04", 2000, status = "incomplete", water = 9f),
        ), water = 2f)
        assertEquals(2f, s.avgHydrationL!!, 0.001f)
        assertEquals(1, s.hydrationGoalDays)
    }

    @Test fun bars_stopAtToday_andMarkUnconfirmed() {
        val wed = LocalDate.parse("2026-11-04")
        val s = sum(week, listOf(
            day("2026-11-02", 2000),
            day("2026-11-03", 1500, status = "incomplete"),
        ), now = wed)
        assertEquals(3, s.bars.size)
        assertEquals(3, s.elapsedDays)
        assertEquals(DayBarState.IN_TARGET, s.bars[0].state)
        assertEquals(DayBarState.UNCONFIRMED, s.bars[1].state)
        assertEquals(1500, s.bars[1].kcal)
        assertNull(s.bars[1].goalKcal)
        assertEquals(DayBarState.MISSING, s.bars[2].state)
    }

    @Test fun noCompleteDays_allAggregatesNull() {
        val s = sum(week, listOf(day("2026-11-02", 1500, status = "incomplete")))
        assertEquals(0, s.completeDays)
        assertNull(s.avgKcal); assertNull(s.balanceVsPlanKcal); assertNull(s.balanceVsTdeeKcal)
        assertNull(s.estimatedKgChange); assertNull(s.macros); assertNull(s.avgHydrationL)
        assertEquals(0, s.hydrationGoalDays)
    }

    @Test fun delta_isPerCompleteDay() {
        val cur = sum(week, listOf(
            day("2026-11-02", 2000, goals = goals(cal = 2000)),
            day("2026-11-03", 2000, goals = goals(cal = 2000)),
        ))
        val prevPeriod = week.previous()
        val prevDays = (0 until 7).map { day(prevPeriod.start.plusDays(it.toLong()).toString(), 2100, goals = goals(cal = 2000)) }
        val prev = sum(prevPeriod, prevDays)
        val d = PeriodStatsCalculator.deltas(cur, prev)
        assertEquals(-100, d.avgKcal)
        // current: 2/2 in target = 100%; previous: 2100 within 10% of 2000 -> 100% => 0
        assertEquals(0, d.inTargetPctPoints)
        // balance per day: 0 vs +100 -> -100 (sums 0 vs 700 are NOT compared)
        assertEquals(-100, d.balancePerDayKcal)
    }

    @Test fun delta_nullWhenPreviousEmpty() {
        val cur = sum(week, listOf(day("2026-11-02", 2000)))
        val prev = sum(week.previous(), emptyList())
        val d = PeriodStatsCalculator.deltas(cur, prev)
        assertNull(d.avgKcal); assertNull(d.inTargetPctPoints); assertNull(d.balancePerDayKcal)
        assertNotNull(cur.avgKcal)
    }
}
