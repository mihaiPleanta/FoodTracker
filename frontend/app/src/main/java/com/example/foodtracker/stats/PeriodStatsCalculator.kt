package com.example.foodtracker.stats

import com.example.foodtracker.model.StatsDayDto
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

enum class DayBarState { IN_TARGET, OFF_TARGET, UNCONFIRMED, MISSING }

data class DayBar(val date: LocalDate, val kcal: Int, val goalKcal: Int?, val state: DayBarState)

data class MacroAverages(
    val proteinG: Float, val carbsG: Float, val fatG: Float,
    val proteinGoalG: Float, val carbsGoalG: Float, val fatGoalG: Float,
)

data class PeriodSummary(
    val elapsedDays: Int,
    val completeDays: Int,
    val inTargetDays: Int,
    val avgKcal: Int?,
    val balanceVsPlanKcal: Int?,
    val balanceVsTdeeKcal: Int?,
    val estimatedKgChange: Float?,
    val macros: MacroAverages?,
    val avgHydrationL: Float?,
    val hydrationGoalDays: Int,
    val bars: List<DayBar>,
)

data class PeriodDeltas(val avgKcal: Int?, val inTargetPctPoints: Int?, val balancePerDayKcal: Int?)

object PeriodStatsCalculator {
    private const val KCAL_PER_KG = 7700f

    fun summarize(
        period: StatsPeriod,
        days: List<StatsDayDto>,
        tolerancePct: Int,
        hydrationGoalL: Float,
        today: LocalDate,
    ): PeriodSummary {
        val byDate = days.associateBy { LocalDate.parse(it.date) }
        val bars = mutableListOf<DayBar>()
        val counting = mutableListOf<StatsDayDto>()

        for (date in period.days()) {
            if (date.isAfter(today)) break
            val d = byDate[date]
            val goals = d?.goals
            val bar = when {
                d == null || !d.hasFood -> DayBar(date, 0, null, DayBarState.MISSING)
                d.status == "complete" && goals != null -> {
                    counting += d
                    val inTarget = abs(d.kcal - goals.calorieGoal) * 100 <= tolerancePct * goals.calorieGoal
                    DayBar(date, d.kcal, goals.calorieGoal,
                        if (inTarget) DayBarState.IN_TARGET else DayBarState.OFF_TARGET)
                }
                else -> DayBar(date, d.kcal, null, DayBarState.UNCONFIRMED)
            }
            bars += bar
        }

        val n = counting.size
        val elapsed = period.elapsedDays(today)
        val inTargetDays = bars.count { it.state == DayBarState.IN_TARGET }
        if (n == 0) {
            return PeriodSummary(elapsed, 0, 0, null, null, null, null, null, null, 0, bars)
        }
        val balanceTdee = counting.sumOf { it.kcal - it.goals!!.tdee }
        return PeriodSummary(
            elapsedDays = elapsed,
            completeDays = n,
            inTargetDays = inTargetDays,
            avgKcal = (counting.sumOf { it.kcal }.toDouble() / n).roundToInt(),
            balanceVsPlanKcal = counting.sumOf { it.kcal - it.goals!!.calorieGoal },
            balanceVsTdeeKcal = balanceTdee,
            estimatedKgChange = balanceTdee / KCAL_PER_KG,
            macros = MacroAverages(
                proteinG = counting.sumOf { it.proteinG.toDouble() }.toFloat() / n,
                carbsG = counting.sumOf { it.carbsG.toDouble() }.toFloat() / n,
                fatG = counting.sumOf { it.fatG.toDouble() }.toFloat() / n,
                proteinGoalG = counting.sumOf { it.goals!!.proteinGoalG }.toFloat() / n,
                carbsGoalG = counting.sumOf { it.goals!!.carbsGoalG }.toFloat() / n,
                fatGoalG = counting.sumOf { it.goals!!.fatGoalG }.toFloat() / n,
            ),
            avgHydrationL = counting.sumOf { it.hydrationL.toDouble() }.toFloat() / n,
            hydrationGoalDays = counting.count { it.hydrationL >= hydrationGoalL },
            bars = bars,
        )
    }

    /** Per-complete-day comparison, so a partial current period is comparable to a full previous one. */
    fun deltas(current: PeriodSummary, previous: PeriodSummary): PeriodDeltas {
        if (current.completeDays == 0 || previous.completeDays == 0) return PeriodDeltas(null, null, null)
        fun pct(s: PeriodSummary) = (100.0 * s.inTargetDays / s.completeDays).roundToInt()
        fun perDay(s: PeriodSummary) = s.balanceVsPlanKcal!!.toDouble() / s.completeDays
        return PeriodDeltas(
            avgKcal = current.avgKcal!! - previous.avgKcal!!,
            inTargetPctPoints = pct(current) - pct(previous),
            balancePerDayKcal = (perDay(current) - perDay(previous)).roundToInt(),
        )
    }
}
