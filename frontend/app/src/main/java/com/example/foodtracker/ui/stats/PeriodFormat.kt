package com.example.foodtracker.ui.stats

import com.example.foodtracker.stats.PeriodKind
import com.example.foodtracker.stats.StatsPeriod
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MINUS = "−"

/** Integer with an explicit sign: "+400", "−300", "0". */
fun signedInt(n: Int): String = when {
    n > 0 -> "+$n"
    n < 0 -> "$MINUS${-n}"
    else -> "0"
}

/** Two-decimal kg value with explicit sign and locale decimal separator: "−0,45". */
fun signedKg(kg: Float, locale: Locale): String {
    val text = String.format(locale, "%.2f", kotlin.math.abs(kg))
    val isZero = text.all { it == '0' || !it.isDigit() }
    return when {
        isZero -> text
        kg > 0 -> "+$text"
        else -> "$MINUS$text"
    }
}

/** Week: "27 Oct – 2 Nov" (Mon–Sun); month: "October 2026" (locale-aware). */
fun periodLabel(period: StatsPeriod, locale: Locale): String = when (period.kind) {
    PeriodKind.WEEK -> {
        val f = DateTimeFormatter.ofPattern("d MMM", locale)
        "${period.start.format(f)} – ${period.end.format(f)}"
    }
    PeriodKind.MONTH -> period.start.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
}
