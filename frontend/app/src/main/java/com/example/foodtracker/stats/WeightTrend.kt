package com.example.foodtracker.stats

import com.example.foodtracker.model.StatsWeightDto
import java.time.LocalDate

data class TrendPoint(val date: LocalDate, val weightKg: Float, val trendKg: Float)

/** Exponentially smoothed weight trend (alpha = 0.1); the first weigh-in seeds the trend. */
object WeightTrend {
    private const val ALPHA = 0.1f

    fun compute(weights: List<StatsWeightDto>): List<TrendPoint> {
        val sorted = weights.map { LocalDate.parse(it.date) to it.weightKg }.sortedBy { it.first }
        val out = ArrayList<TrendPoint>(sorted.size)
        var trend = 0f
        sorted.forEachIndexed { i, (date, kg) ->
            trend = if (i == 0) kg else trend + ALPHA * (kg - trend)
            out.add(TrendPoint(date, kg, trend))
        }
        return out
    }

    /** Trend at the last point in the period minus trend at the last point before it; null if either is missing. */
    fun periodChange(points: List<TrendPoint>, period: StatsPeriod): Float? {
        val last = points.lastOrNull { !it.date.isBefore(period.start) && !it.date.isAfter(period.end) }
            ?: return null
        val before = points.lastOrNull { it.date.isBefore(period.start) } ?: return null
        return last.trendKg - before.trendKg
    }
}
