package com.example.foodtracker.stats

import com.example.foodtracker.data.journal.LocalDay
import com.example.foodtracker.model.DayStatus
import com.example.foodtracker.model.LoggedFood
import com.example.foodtracker.model.StatsDayDto
import com.example.foodtracker.model.StatsDaysResponseDto
import com.example.foodtracker.model.StatsGoalsDto
import com.example.foodtracker.model.StatsWeightDto
import com.example.foodtracker.model.TopFoodStatDto
import com.example.foodtracker.model.TopFoodsDto
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

private const val WEIGHT_WARMUP_DAYS = 60L
private const val TOP_FOODS_LIMIT = 5

/**
 * Port of `backend/routers/stats.py` (`stats_days` + `_top_foods`) over the local mirror, so the
 * Week/Month statistics work offline. Rounding follows Python: `round()` is half-even.
 */
object LocalStatsBuilder {

    fun build(
        days: List<LocalDay>,
        dayGoals: Map<LocalDate, StatsGoalsDto?>,
        weights: List<Pair<LocalDate, Float>>,
        from: LocalDate,
        to: LocalDate,
        provisionalGoals: StatsGoalsDto?,
    ): StatsDaysResponseDto {
        val inRange = days.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        val out = inRange.sortedBy { it.date }.mapNotNull { day ->
            val logs = day.meals.values.flatten()
            if (logs.isEmpty() && day.status == null && day.hydrationL <= 0f) return@mapNotNull null
            var kcal = 0.0; var protein = 0.0; var carbs = 0.0; var fat = 0.0
            for (l in logs) {
                val f = l.grams / 100.0
                kcal += l.food.per100g * f
                protein += l.food.protein100g.asDecimal() * f
                carbs += l.food.carbs100g.asDecimal() * f
                fat += l.food.fat100g.asDecimal() * f
            }
            val complete = day.status == DayStatus.COMPLETE
            StatsDayDto(
                date = day.date.toString(),
                kcal = roundHalfEven(kcal, 0).toInt(),
                proteinG = roundHalfEven(protein, 1).toFloat(),
                carbsG = roundHalfEven(carbs, 1).toFloat(),
                fatG = roundHalfEven(fat, 1).toFloat(),
                hasFood = logs.isNotEmpty(),
                status = when (day.status) {
                    DayStatus.COMPLETE -> "complete"
                    DayStatus.INCOMPLETE -> "incomplete"
                    null -> null
                },
                goals = if (complete) dayGoals[day.date] ?: provisionalGoals else null,
                hydrationL = day.hydrationL.coerceAtLeast(0f),
            )
        }
        val weightFrom = from.minusDays(WEIGHT_WARMUP_DAYS)
        val weightsOut = weights
            .filter { !it.first.isBefore(weightFrom) && !it.first.isAfter(to) }
            .sortedBy { it.first }
            .map { StatsWeightDto(it.first.toString(), it.second) }
        val completeDays = inRange.filter { it.status == DayStatus.COMPLETE }
        return StatsDaysResponseDto(days = out, weights = weightsOut, topFoods = topFoods(completeDays))
    }

    private class Group {
        var kcal = 0.0
        var protein = 0.0
        val dates = HashSet<LocalDate>()
        var latestDate: LocalDate? = null
        var latestKey = 0L
        var row: LoggedFood? = null
    }

    private fun topFoods(completeDays: List<LocalDay>): TopFoodsDto {
        val groups = LinkedHashMap<String, Group>()
        for (day in completeDays) {
            for (l in day.meals.values.flatten()) {
                val key = if (l.food.barcode == "recipe") "recipe:" + l.food.name else l.food.barcode
                val g = groups.getOrPut(key) { Group() }
                val f = l.grams / 100.0
                g.kcal += l.food.per100g * f
                g.protein += l.food.protein100g.asDecimal() * f
                g.dates += day.date
                // Server orders by (log_date, created_at); the mirror's sortKey carries created_at for server rows.
                val sk = l.sortKey ?: 0L
                val ld = g.latestDate
                if (ld == null || day.date > ld || (day.date == ld && sk >= g.latestKey)) {
                    g.latestDate = day.date; g.latestKey = sk; g.row = l
                }
            }
        }
        val stats = groups.map { (key, g) ->
            val row = g.row!!
            TopFoodStatDto(
                key = key,
                name = row.food.name,
                brand = row.food.brand,
                totalKcal = roundHalfEven(g.kcal, 0).toInt(),
                totalProteinG = roundHalfEven(g.protein, 1).toFloat(),
                daysCount = g.dates.size,
            )
        }
        return TopFoodsDto(
            byKcal = stats.sortedWith(compareBy({ -it.totalKcal }, { it.name })).take(TOP_FOODS_LIMIT),
            byProtein = stats.sortedWith(compareBy({ -it.totalProteinG }, { it.name })).take(TOP_FOODS_LIMIT),
        )
    }

    /** Float as the decimal it prints (the server holds 0.35, not 0.3499999940395355). */
    private fun Float.asDecimal(): Double = toString().toDouble()

    /** Python `round(x, digits)`: exact binary value of [x], ties to even. */
    private fun roundHalfEven(x: Double, digits: Int): BigDecimal =
        BigDecimal(x).setScale(digits, RoundingMode.HALF_EVEN)
}
