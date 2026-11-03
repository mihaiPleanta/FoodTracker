package com.example.foodtracker.stats

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class PeriodKind { WEEK, MONTH }

/** Calendar period: week = Monday..Sunday, month = 1..last day. */
data class StatsPeriod(val kind: PeriodKind, val start: LocalDate) {
    val end: LocalDate
        get() = when (kind) {
            PeriodKind.WEEK -> start.plusDays(6)
            PeriodKind.MONTH -> start.plusMonths(1).minusDays(1)
        }

    fun previous(): StatsPeriod = StatsPeriod(
        kind,
        when (kind) {
            PeriodKind.WEEK -> start.minusWeeks(1)
            PeriodKind.MONTH -> start.minusMonths(1)
        },
    )

    fun next(): StatsPeriod = StatsPeriod(
        kind,
        when (kind) {
            PeriodKind.WEEK -> start.plusWeeks(1)
            PeriodKind.MONTH -> start.plusMonths(1)
        },
    )

    fun days(): List<LocalDate> {
        val n = ChronoUnit.DAYS.between(start, end).toInt() + 1
        return (0 until n).map { start.plusDays(it.toLong()) }
    }

    /** Days in [start, min(end, today)]; 0 if the period starts after today. */
    fun elapsedDays(today: LocalDate): Int {
        if (start.isAfter(today)) return 0
        val last = if (end.isBefore(today)) end else today
        return ChronoUnit.DAYS.between(start, last).toInt() + 1
    }

    fun isCurrent(today: LocalDate): Boolean = !start.isAfter(today) && !end.isBefore(today)

    companion object {
        fun containing(kind: PeriodKind, date: LocalDate): StatsPeriod = StatsPeriod(
            kind,
            when (kind) {
                PeriodKind.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                PeriodKind.MONTH -> date.withDayOfMonth(1)
            },
        )
    }
}
