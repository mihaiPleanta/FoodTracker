package com.example.foodtracker.ui.stats

import com.example.foodtracker.model.FoodItem
import com.example.foodtracker.model.TopFoodStatDto
import com.example.foodtracker.stats.DayBarState
import com.example.foodtracker.stats.StatsPeriod
import com.example.foodtracker.stats.TrendPoint
import androidx.compose.ui.graphics.Color
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

/** Colour of a day in bars and calendar; null when nothing is drawn (missing). */
fun dayStateColor(state: DayBarState, green: Color, blue: Color, grey: Color): Color? = when (state) {
    DayBarState.IN_TARGET -> green
    DayBarState.OFF_TARGET -> blue
    DayBarState.UNCONFIRMED -> grey
    DayBarState.MISSING -> null
}

/** Empty cells before day 1 of a Monday-first grid (Monday = 0 ... Sunday = 6). */
fun calendarLeadingBlanks(monthStart: LocalDate): Int = monthStart.dayOfWeek.value - DayOfWeek.MONDAY.value

/** Calendar cells row by row: null = blank (leading blank or trailing fill up to a full week). */
fun calendarCells(period: StatsPeriod): List<LocalDate?> {
    val days = period.days()
    val cells = ArrayList<LocalDate?>()
    repeat(calendarLeadingBlanks(period.start)) { cells.add(null) }
    cells.addAll(days)
    while (cells.size % 7 != 0) cells.add(null)
    return cells
}

/** Weigh-ins that fall inside the period (the trend itself is computed over the warm-up too). */
fun pointsInPeriod(points: List<TrendPoint>, period: StatsPeriod): List<TrendPoint> =
    points.filter { !it.date.isBefore(period.start) && !it.date.isAfter(period.end) }

/** Hydration average as "2,1" in the given locale. */
fun formatLitres(l: Float, locale: Locale): String = String.format(locale, "%.1f", l)

/** Total shown on a top-food row: "1.250 kcal" or "84 g" depending on the active toggle. */
fun topFoodTotal(food: TopFoodStatDto, byProtein: Boolean, locale: Locale): String =
    if (byProtein) String.format(locale, "%d g", Math.round(food.totalProteinG))
    else String.format(locale, "%,d kcal", food.totalKcal)

/** Minimal item so ProductThumbnail falls back to its emoji (the stats DTO has no image/categories). */
fun TopFoodStatDto.toThumbnailItem(): FoodItem = FoodItem(
    barcode = key, name = name, brand = brand, imageUrl = null, categories = emptyList(),
    per100g = 0, protein100g = 0f, carbs100g = 0f, fat100g = 0f,
)
