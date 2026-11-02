package com.example.foodtracker.ui.customfood

import com.example.foodtracker.R
import com.example.foodtracker.model.CustomFoodDto
import com.example.foodtracker.model.CustomFoodInDto
import com.example.foodtracker.model.ServingDto
import com.example.foodtracker.util.label.ParsedLabel
import java.math.BigDecimal
import kotlin.math.abs

enum class FormField { NAME, BRAND, BARCODE, KCAL, PROTEIN, CARBS, FAT, SERVING_UNIT, SERVING_GRAMS }

/**
 * Starea formularului de produs propriu (Kotlin pur, fără Context).
 * [autoFilled] = câmpuri completate din eticheta fotografiată și încă needitate („verifică”).
 */
data class CustomFoodForm(
    val values: Map<FormField, String> = emptyMap(),
    val autoFilled: Set<FormField> = emptySet(),
) {
    private fun text(f: FormField): String = values[f]?.trim().orEmpty()

    private fun number(f: FormField): Float? = text(f).replace(',', '.').toFloatOrNull()

    fun edit(field: FormField, text: String): CustomFoodForm =
        copy(values = values + (field to text), autoFilled = autoFilled - field)

    /**
     * Completează doar câmpurile non-null și goale; nu suprascrie ce a tastat utilizatorul.
     * [defaultServingUnit] (localizat de apelant) înlocuiește unitatea lipsă când parserul
     * găsește doar o porție generică („porție = 30 g”).
     */
    fun applyParsed(p: ParsedLabel, defaultServingUnit: String): CustomFoodForm {
        var v = values
        val filled = autoFilled.toMutableSet()
        fun fill(f: FormField, value: String?) {
            if (value != null && text(f).isEmpty()) {
                v = v + (f to value)
                filled += f
            }
        }
        fill(FormField.KCAL, p.kcal?.let(::formatNumber))
        fill(FormField.PROTEIN, p.protein?.let(::formatNumber))
        fill(FormField.CARBS, p.carbs?.let(::formatNumber))
        fill(FormField.FAT, p.fat?.let(::formatNumber))
        fill(FormField.SERVING_GRAMS, p.servingGrams?.let(::formatNumber))
        val unit = p.servingUnit ?: if (p.servingGrams != null) defaultServingUnit else null
        fill(FormField.SERVING_UNIT, unit)
        return copy(values = v, autoFilled = filled)
    }

    fun validate(): Map<FormField, Int> {
        val errors = linkedMapOf<FormField, Int>()
        if (text(FormField.NAME).isEmpty()) errors[FormField.NAME] = R.string.custom_food_error_required

        val kcal = number(FormField.KCAL)
        when {
            text(FormField.KCAL).isEmpty() -> errors[FormField.KCAL] = R.string.custom_food_error_required
            kcal == null || kcal < 0f || kcal > 900f -> errors[FormField.KCAL] = R.string.custom_food_error_range
        }
        for (f in listOf(FormField.PROTEIN, FormField.CARBS, FormField.FAT)) {
            val n = number(f)
            when {
                text(f).isEmpty() -> errors[f] = R.string.custom_food_error_required
                n == null || n < 0f || n > 100f -> errors[f] = R.string.custom_food_error_range
            }
        }
        if (FormField.PROTEIN !in errors && FormField.CARBS !in errors && FormField.FAT !in errors) {
            val sum = number(FormField.PROTEIN)!! + number(FormField.CARBS)!! + number(FormField.FAT)!!
            if (sum > 100f) errors[FormField.FAT] = R.string.custom_food_error_macro_sum
        }

        val barcode = text(FormField.BARCODE)
        if (barcode.isNotEmpty() && !BARCODE_REGEX.matches(barcode)) {
            errors[FormField.BARCODE] = R.string.custom_food_error_barcode
        }

        val unit = text(FormField.SERVING_UNIT)
        val gramsText = text(FormField.SERVING_GRAMS)
        if (unit.isNotEmpty() || gramsText.isNotEmpty()) {
            val grams = number(FormField.SERVING_GRAMS)
            if (unit.isEmpty()) errors[FormField.SERVING_UNIT] = R.string.custom_food_error_serving_pair
            if (grams == null || grams <= 0f) errors[FormField.SERVING_GRAMS] = R.string.custom_food_error_serving_pair
        }
        return errors
    }

    /** Avertisment nebloant: kcal declarate vs. 4P + 4C + 9G. False dacă lipsește vreo valoare. */
    val kcalMismatch: Boolean
        get() {
            val kcal = number(FormField.KCAL) ?: return false
            val p = number(FormField.PROTEIN) ?: return false
            val c = number(FormField.CARBS) ?: return false
            val g = number(FormField.FAT) ?: return false
            val diff = abs(4 * p + 4 * c + 9 * g - kcal)
            return diff > 0.2f * kcal && diff > 20f
        }

    fun toInDto(): CustomFoodInDto? {
        if (validate().isNotEmpty()) return null
        val unit = text(FormField.SERVING_UNIT)
        val serving = if (unit.isNotEmpty()) ServingDto(unit, number(FormField.SERVING_GRAMS)!!) else null
        return CustomFoodInDto(
            name = text(FormField.NAME),
            brand = text(FormField.BRAND).ifEmpty { null },
            barcode = text(FormField.BARCODE).ifEmpty { null },
            kcal100g = number(FormField.KCAL)!!,
            protein100g = number(FormField.PROTEIN)!!,
            carbs100g = number(FormField.CARBS)!!,
            fat100g = number(FormField.FAT)!!,
            serving = serving,
        )
    }

    companion object {
        private val BARCODE_REGEX = Regex("^\\d{8,13}$")

        private fun formatNumber(f: Float): String =
            BigDecimal(f.toString()).stripTrailingZeros().toPlainString()

        fun fromDto(d: CustomFoodDto): CustomFoodForm {
            val m = mutableMapOf(
                FormField.NAME to d.name,
                FormField.KCAL to formatNumber(d.kcal100g),
                FormField.PROTEIN to formatNumber(d.protein100g),
                FormField.CARBS to formatNumber(d.carbs100g),
                FormField.FAT to formatNumber(d.fat100g),
            )
            d.brand?.let { m[FormField.BRAND] = it }
            d.barcode?.let { m[FormField.BARCODE] = it }
            d.serving?.let {
                m[FormField.SERVING_UNIT] = it.unit
                m[FormField.SERVING_GRAMS] = formatNumber(it.grams)
            }
            return CustomFoodForm(values = m)
        }

        fun prefilled(barcode: String?, name: String?): CustomFoodForm {
            val m = mutableMapOf<FormField, String>()
            if (!barcode.isNullOrBlank()) m[FormField.BARCODE] = barcode.trim()
            if (!name.isNullOrBlank()) m[FormField.NAME] = name.trim()
            return CustomFoodForm(values = m)
        }
    }
}
