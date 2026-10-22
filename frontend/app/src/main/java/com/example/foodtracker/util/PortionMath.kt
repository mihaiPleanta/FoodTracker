package com.example.foodtracker.util

import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.foodtracker.R
import com.example.foodtracker.model.PortionUnit
import com.example.foodtracker.model.UnitSource
import java.text.DecimalFormatSymbols
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Same keys as the backend's standard units. */
val STANDARD_UNITS: Set<String> = setOf(
    "tsp", "tbsp", "slice", "piece", "piece_small", "piece_medium", "piece_large",
    "serving", "package", "cup", "glass", "bowl", "handful", "ml",
)

fun gramsFor(qty: Float, gramsPerUnit: Float): Int = max(1, (qty * gramsPerUnit).roundToInt())

/** Stepper increment: ±50 for millilitres, ±½ for everything else. */
fun stepFor(unit: String?): Float = if (unit == "ml") 50f else 0.5f

/** Largest quantity accepted: 5000 ml, otherwise 999 units. */
fun maxQtyFor(unit: String?): Float = if (unit == "ml") 5000f else 999f

fun parseQty(text: String, unit: String? = null): Float? {
    val v = text.trim().replace(',', '.').toFloatOrNull() ?: return null
    return if (v.isFinite() && v > 0f && v <= maxQtyFor(unit)) v else null
}

fun stepQty(qty: Float, delta: Float, floor: Float = 0.5f): Float = max(floor, qty + delta)

fun formatQty(qty: Float, decimalSeparator: Char): String {
    if (qty == 0.5f) return "½"
    if (qty == qty.toInt().toFloat()) return qty.toInt().toString()
    return String.format(java.util.Locale.ROOT, "%.1f", qty).replace('.', decimalSeparator)
}

fun pluralCount(qty: Float): Int = max(1, ceil(qty).toInt())

@PluralsRes
fun unitPluralRes(unit: String): Int? = when (unit) {
    "tsp" -> R.plurals.portion_unit_tsp
    "tbsp" -> R.plurals.portion_unit_tbsp
    "slice" -> R.plurals.portion_unit_slice
    "piece" -> R.plurals.portion_unit_piece
    "piece_small" -> R.plurals.portion_unit_piece_small
    "piece_medium" -> R.plurals.portion_unit_piece_medium
    "piece_large" -> R.plurals.portion_unit_piece_large
    "serving" -> R.plurals.portion_unit_serving
    "package" -> R.plurals.portion_unit_package
    "cup" -> R.plurals.portion_unit_cup
    "glass" -> R.plurals.portion_unit_glass
    "bowl" -> R.plurals.portion_unit_bowl
    "handful" -> R.plurals.portion_unit_handful
    "ml" -> R.plurals.portion_unit_ml
    else -> null
}

data class PortionChoice(val grams: Int, val unit: String?, val unitQty: Float?)

fun mergeLoggedUnit(units: List<PortionUnit>, logged: PortionChoice): List<PortionUnit> {
    val unit = logged.unit ?: return units
    val qty = logged.unitQty ?: return units
    if (qty <= 0f || units.any { it.unit == unit }) return units
    return units + PortionUnit(unit, logged.grams / qty, UnitSource.USER)
}

/** "2 lingurițe · 14 g" / "2 teaspoons · 14 g"; without a unit just "14g". */
@Composable
fun portionText(grams: Int, unit: String?, unitQty: Float?): String {
    if (unit == null || unitQty == null) return "${grams}g"
    val locale = LocalConfiguration.current.locales[0]
    val qtyText = formatQty(unitQty, DecimalFormatSymbols.getInstance(locale).decimalSeparator)
    val res = unitPluralRes(unit)
    val label = if (res != null) {
        pluralStringResource(res, pluralCount(unitQty), qtyText)
    } else {
        "$qtyText $unit"
    }
    return stringResource(R.string.portion_text_format, label, grams)
}
