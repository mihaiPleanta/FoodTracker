package com.example.foodtracker.stats

import com.example.foodtracker.data.journal.LocalDay
import com.example.foodtracker.model.DayStatus
import com.example.foodtracker.model.FoodItem
import com.example.foodtracker.model.LoggedFood
import com.example.foodtracker.model.MEAL_ORDER
import com.example.foodtracker.model.StatsGoalsDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LocalStatsBuilderTest {
    private fun d(s: String) = LocalDate.parse(s)

    private fun food(
        barcode: String = "111", name: String = "Yogurt", brand: String? = null,
        kcal: Int = 60, p: Float = 4f, c: Float = 5f, f: Float = 2f,
    ) = FoodItem(barcode = barcode, name = name, brand = brand, per100g = kcal, protein100g = p, carbs100g = c, fat100g = f)

    private fun log(food: FoodItem, grams: Int, sortKey: Long = 1L) = LoggedFood(food, grams, sortKey = sortKey)

    private fun day(
        date: String, logs: List<LoggedFood> = emptyList(), water: Float = 0f, status: DayStatus? = null,
    ) = LocalDay(
        date = d(date),
        meals = MEAL_ORDER.associateWith { if (it == "Lunch") logs else emptyList() },
        hydrationL = water, status = status, weightKg = null, fetched = true,
    )

    private val snap = StatsGoalsDto(2000, 150, 250, 65, 2400)
    private val prov = StatsGoalsDto(1800, 100, 200, 60, 2100)

    private fun build(
        days: List<LocalDay>, goals: Map<LocalDate, StatsGoalsDto?> = emptyMap(),
        weights: List<Pair<LocalDate, Float>> = emptyList(),
        from: String = "2026-05-20", to: String = "2026-05-24", provisional: StatsGoalsDto? = null,
    ) = LocalStatsBuilder.build(days, goals, weights, d(from), d(to), provisional)

    /** Same inputs as backend/tests/test_stats.py (_LOG = Yogurt 200 g 60/4/5/2, Bar 50 g 100/10/20/1,
     *  recipes, hydration-only day, weights with 60-day warm-up), combined into one range. */
    @Test fun buildMatchesServerFixture() {
        val yogurt = food()
        val bar = food("222", "Bar", kcal = 100, p = 10f, c = 20f, f = 1f)
        val pasta = food("recipe", "Pasta", kcal = 200, p = 10f, c = 30f, f = 5f)
        val days = listOf(
            day("2026-05-20", listOf(log(yogurt, 200, 1), log(bar, 50, 2), log(pasta, 100, 3)), status = DayStatus.COMPLETE),
            day("2026-05-21", listOf(
                log(yogurt, 200, 4),
                log(yogurt.copy(name = "Yogurt Plus", brand = "Acme"), 200, 9), // latest row names the group
                log(pasta, 100, 5),
            ), status = DayStatus.COMPLETE),
            day("2026-05-22", listOf(log(bar, 50, 6)), water = 1.5f, status = DayStatus.INCOMPLETE),
            day("2026-05-23", water = 1.5f),
            day("2026-05-24"), // nothing: not included
        )
        val frm = d("2026-05-20")
        val weights = listOf(-70L to 90f, 3L to 77f, -50L to 80f, 0L to 78f, 5L to 70f).map { frm.plusDays(it.first) to it.second }
        val r = build(
            days, mapOf(d("2026-05-20") to snap, d("2026-05-21") to snap, d("2026-05-22") to snap), weights,
        )

        assertEquals(listOf("2026-05-20", "2026-05-21", "2026-05-22", "2026-05-23"), r.days.map { it.date })
        val d20 = r.days[0]
        assertEquals(370, d20.kcal)          // 120 + 50 + 200
        assertEquals(23.0f, d20.proteinG)
        assertEquals(50.0f, d20.carbsG)
        assertEquals(9.5f, d20.fatG)
        assertTrue(d20.hasFood)
        assertEquals("complete", d20.status)
        assertEquals(snap, d20.goals)
        assertEquals(0f, d20.hydrationL)
        assertEquals(440, r.days[1].kcal)
        assertEquals(26.0f, r.days[1].proteinG)
        // incomplete day: status kept, goals never exposed
        assertEquals("incomplete", r.days[2].status)
        assertEquals(null, r.days[2].goals)
        assertEquals(1.5f, r.days[2].hydrationL)
        // hydration-only day
        assertFalse(r.days[3].hasFood)
        assertEquals(0, r.days[3].kcal)
        assertEquals(1.5f, r.days[3].hydrationL)

        assertEquals(
            listOf(frm.minusDays(50) to 80f, frm to 78f, frm.plusDays(3) to 77f),
            r.weights.map { d(it.date) to it.weightKg },
        )

        // only complete days (05-20, 05-21) count; the incomplete Bar log is excluded
        assertEquals(listOf("recipe:Pasta", "111", "222"), r.topFoods.byKcal.map { it.key })
        assertEquals(listOf(400, 360, 50), r.topFoods.byKcal.map { it.totalKcal })
        assertEquals(listOf("111", "recipe:Pasta", "222"), r.topFoods.byProtein.map { it.key })
        val y = r.topFoods.byProtein[0]
        assertEquals("Yogurt Plus", y.name)
        assertEquals("Acme", y.brand)
        assertEquals(24.0f, y.totalProteinG)
        assertEquals(2, y.daysCount)
        assertEquals(listOf(2, 2, 1), r.topFoods.byKcal.map { it.daysCount })
    }

    @Test fun aggregatesOneDayLikeServer() { // test_stats_days_aggregates_logs_per_day
        val r = build(listOf(day("2026-05-20", listOf(
            log(food(), 200), log(food("222", "Bar", kcal = 100, p = 10f, c = 20f, f = 1f), 50),
        ))), from = "2026-05-20", to = "2026-05-20")
        val x = r.days.single()
        assertEquals(170, x.kcal); assertEquals(13.0f, x.proteinG); assertEquals(20.0f, x.carbsG); assertEquals(4.5f, x.fatG)
        assertEquals(null, x.status); assertEquals(null, x.goals)
        assertTrue(r.topFoods.byKcal.isEmpty() && r.topFoods.byProtein.isEmpty())
    }

    @Test fun completeDayWithoutSnapshotUsesProvisionalGoals() {
        val days = listOf(day("2026-05-20", listOf(log(food(), 100)), status = DayStatus.COMPLETE))
        assertEquals(prov, build(days, provisional = prov).days.single().goals)
        assertEquals(null, build(days, provisional = null).days.single().goals)
        // an existing snapshot wins
        assertEquals(snap, build(days, mapOf(d("2026-05-20") to snap), provisional = prov).days.single().goals)
    }

    @Test fun completeDayWithoutLogsHasNoFood() {
        val r = build(listOf(day("2026-05-20", status = DayStatus.COMPLETE)), mapOf(d("2026-05-20") to snap))
        assertFalse(r.days.single().hasFood)
        assertEquals(0, r.days.single().kcal)
        assertEquals(snap, r.days.single().goals)
    }

    @Test fun recipesGroupByName() {
        val days = listOf(day("2026-05-20", listOf(
            log(food("recipe", "Pasta"), 100), log(food("recipe", "Soup"), 100),
        ), status = DayStatus.COMPLETE))
        val r = build(days, from = "2026-05-20", to = "2026-05-20")
        assertEquals(listOf("recipe:Pasta", "recipe:Soup"), r.topFoods.byKcal.map { it.key })
    }

    @Test fun topFoodsLimitFiveSortedByTotalThenName() {
        val logs = (0 until 7).map { log(food("b$it", "F$it", kcal = 60), 100) }
        val r = build(listOf(day("2026-05-20", logs, status = DayStatus.COMPLETE)), from = "2026-05-20", to = "2026-05-20")
        assertEquals(listOf("F0", "F1", "F2", "F3", "F4"), r.topFoods.byKcal.map { it.name })
        assertEquals(5, r.topFoods.byProtein.size)
    }

    @Test fun emptyHydrationIsOmitted() { // test_stats_days_hydration_only_day_present
        val r = build(listOf(day("2026-05-20", water = 1.5f), day("2026-05-21", water = 0f)), to = "2026-05-22")
        assertEquals(listOf("2026-05-20"), r.days.map { it.date })
        assertEquals(1.5f, r.days.single().hydrationL)
        assertFalse(r.days.single().hasFood)
    }

    @Test fun outOfRangeDaysAreExcluded() {
        val r = build(listOf(
            day("2026-05-19", listOf(log(food(), 100))), day("2026-05-20", listOf(log(food(), 100))),
            day("2026-05-22", listOf(log(food(), 100))),
        ), from = "2026-05-20", to = "2026-05-21")
        assertEquals(listOf("2026-05-20"), r.days.map { it.date })
    }

    @Test fun roundingIsHalfEvenLikePython() {
        // kcal: 0.5 -> 0, 1.5 -> 2, 2.5 -> 2 (Python round); macro: round(0.25, 1) == 0.2, round(0.35,1) -> exact double 0.34999.. -> 0.3
        fun kcalOf(per: Int, grams: Int) = build(listOf(day("2026-05-20", listOf(log(food(kcal = per), grams)))),
            from = "2026-05-20", to = "2026-05-20").days.single().kcal
        assertEquals(0, kcalOf(1, 50))
        assertEquals(2, kcalOf(3, 50))
        assertEquals(2, kcalOf(5, 50))
        assertEquals(4, kcalOf(7, 50)) // 3.5 -> 4
        val r = build(listOf(day("2026-05-20", listOf(log(food(p = 0.25f, c = 0.75f, f = 0.35f), 100)))),
            from = "2026-05-20", to = "2026-05-20")
        assertEquals(0.2f, r.days.single().proteinG)
        assertEquals(0.8f, r.days.single().carbsG)
        assertEquals(0.3f, r.days.single().fatG) // 0.35f as the decimal 0.35 is below the tie in binary
    }
}
