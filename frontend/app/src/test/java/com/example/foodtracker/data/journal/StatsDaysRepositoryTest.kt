package com.example.foodtracker.data.journal

import com.example.foodtracker.TestJournal
import com.example.foodtracker.model.FoodItem
import com.example.foodtracker.model.LoggedFood
import com.example.foodtracker.model.StatsGoalsDto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatsDaysRepositoryTest {
    private val today = LocalDate.of(2026, 11, 4)
    private val snap = StatsGoalsDto(2000, 150, 250, 65, 2400)
    private val prov = StatsGoalsDto(1800, 100, 200, 60, 2100)
    private fun food(name: String = "Yogurt") =
        FoodItem(barcode = "111", name = name, per100g = 60, protein100g = 4f, carbs100g = 5f, fat100g = 2f)

    private suspend fun fetched(j: TestJournal, date: String, status: String? = null, goals: StatsGoalsDto? = null, water: Float = 0f) {
        j.dao.upsertDays(listOf(DayEntity(date, water, status, JournalJson.encodeGoals(goals), 1L)))
    }

    @Test fun notCoveredReturnsNull() = runTest {
        val j = TestJournal(today = { today })
        // nothing downloaded
        assertNull(j.repo.statsDays(today.minusDays(6), today, null))
        // before the mirror window
        assertNull(j.repo.statsDays(today.minusDays(70), today.minusDays(64), null))
    }

    @Test fun coveredRangeBuildsFromMirror() = runTest {
        val j = TestJournal(today = { today })
        for (i in 0..6) fetched(j, today.minusDays(i.toLong()).toString())
        fetched(j, "2026-11-03", status = "complete", goals = snap, water = 2f)
        fetched(j, "2026-11-02", status = "complete") // no snapshot -> provisional
        j.repo.addLog(LocalDate.of(2026, 11, 3), "Lunch", LoggedFood(food(), 200), sortKey = 5)
        j.dao.upsertWeights(listOf(WeightEntity("2026-11-01", 80f, false), WeightEntity("2026-08-01", 90f, false)))

        val r = j.repo.statsDays(today.minusDays(6), today, prov)!!
        val d3 = r.days.single { it.date == "2026-11-03" }
        assertEquals(120, d3.kcal); assertTrue(d3.hasFood); assertEquals(2f, d3.hydrationL); assertEquals(snap, d3.goals)
        assertEquals(prov, r.days.single { it.date == "2026-11-02" }.goals)
        assertEquals(listOf("2026-11-01"), r.weights.map { it.date })
        assertEquals(listOf("111"), r.topFoods.byKcal.map { it.key })
    }

    @Test fun currentPeriodWithFutureDaysIsStillServedLocally() = runTest {
        val j = TestJournal(today = { today })
        for (i in 0..6) fetched(j, today.minusDays(i.toLong()).toString())
        // Wed 4 Nov: the week ends Sun 8 Nov (future days hold no data)
        val r = j.repo.statsDays(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8), null)
        assertTrue(r != null)
    }
}
