package com.example.foodtracker.ui.customfood

import com.example.foodtracker.R
import com.example.foodtracker.model.CustomFoodDto
import com.example.foodtracker.model.ServingDto
import com.example.foodtracker.util.label.ParsedLabel
import org.junit.Assert.*
import org.junit.Test

class CustomFoodFormTest {

    private fun form(vararg p: Pair<FormField, String>) = CustomFoodForm(values = mapOf(*p))

    private fun valid() = form(
        FormField.NAME to "Iaurt", FormField.KCAL to "100",
        FormField.PROTEIN to "5", FormField.CARBS to "10", FormField.FAT to "4",
    )

    @Test fun commaDecimal_isAccepted() {
        val dto = valid().edit(FormField.PROTEIN, "6,1").toInDto()!!
        assertEquals(6.1f, dto.protein100g, 0.001f)
    }

    @Test fun required_fields_missing() {
        val e = CustomFoodForm().validate()
        for (f in listOf(FormField.NAME, FormField.KCAL, FormField.PROTEIN, FormField.CARBS, FormField.FAT))
            assertEquals(R.string.custom_food_error_required, e[f])
        assertNull(CustomFoodForm().toInDto())
    }

    @Test fun ranges_and_macroSum() {
        assertEquals(R.string.custom_food_error_range, valid().edit(FormField.KCAL, "901").validate()[FormField.KCAL])
        val f = valid().edit(FormField.PROTEIN, "40").edit(FormField.CARBS, "40").edit(FormField.FAT, "30")
        assertEquals(R.string.custom_food_error_macro_sum, f.validate()[FormField.FAT])
        assertEquals(R.string.custom_food_error_range, valid().edit(FormField.FAT, "-1").validate()[FormField.FAT])
        assertEquals(R.string.custom_food_error_range, valid().edit(FormField.CARBS, "101").validate()[FormField.CARBS])
    }

    @Test fun barcode_format() {
        assertEquals(R.string.custom_food_error_barcode, valid().edit(FormField.BARCODE, "12ab").validate()[FormField.BARCODE])
        assertEquals(R.string.custom_food_error_barcode, valid().edit(FormField.BARCODE, "1234567").validate()[FormField.BARCODE])
        assertNull(valid().edit(FormField.BARCODE, "").validate()[FormField.BARCODE])
        assertNull(valid().edit(FormField.BARCODE, "12345678").validate()[FormField.BARCODE])
    }

    @Test fun serving_needs_both() {
        val one = valid().edit(FormField.SERVING_UNIT, "felie")
        assertEquals(R.string.custom_food_error_serving_pair, one.validate()[FormField.SERVING_GRAMS])
        val both = one.edit(FormField.SERVING_GRAMS, "30")
        assertTrue(both.validate().isEmpty())
        assertEquals(ServingDto("felie", 30f), both.toInDto()!!.serving)
        assertNull(valid().toInDto()!!.serving)
    }

    @Test fun emptyBrandAndBarcode_becomeNull() {
        val d = valid().edit(FormField.BRAND, " ").toInDto()!!
        assertNull(d.brand); assertNull(d.barcode)
    }

    @Test fun kcalMismatch_rule() {
        fun f(kcal: String, p: String, c: String, g: String) = form(
            FormField.KCAL to kcal, FormField.PROTEIN to p, FormField.CARBS to c, FormField.FAT to g)
        assertFalse(f("452", "6.1", "68", "17").kcalMismatch)
        assertTrue(f("250", "6.1", "68", "17").kcalMismatch)
        assertFalse(f("10", "1", "1", "1").kcalMismatch) // calc 17 vs 10: diff 7 < 20
        assertFalse(CustomFoodForm().kcalMismatch)
    }

    @Test fun applyParsed_marksAndDoesNotOverwriteTyped() {
        val r = form(FormField.FAT to "20").applyParsed(ParsedLabel(kcal = 452f, fat = 17f, protein = null, carbs = null, servingGrams = null, servingUnit = null), "porție")
        assertEquals("20", r.values[FormField.FAT])
        assertEquals("452", r.values[FormField.KCAL])
        assertEquals(setOf(FormField.KCAL), r.autoFilled)
    }

    @Test fun applyParsed_formatsDecimals() {
        val r = CustomFoodForm().applyParsed(ParsedLabel(kcal = null, protein = 6.1f, carbs = 68f, fat = null, servingGrams = null, servingUnit = null), "porție")
        assertEquals("6.1", r.values[FormField.PROTEIN]); assertEquals("68", r.values[FormField.CARBS])
    }

    @Test fun applyParsed_namedUnitKept() {
        val r = CustomFoodForm().applyParsed(ParsedLabel(kcal = null, protein = null, carbs = null, fat = null, servingGrams = 25f, servingUnit = "felie"), "porție")
        assertEquals("felie", r.values[FormField.SERVING_UNIT]); assertEquals("25", r.values[FormField.SERVING_GRAMS])
        assertEquals(setOf(FormField.SERVING_UNIT, FormField.SERVING_GRAMS), r.autoFilled)
    }

    @Test fun applyParsed_genericPortion_usesDefaultUnit() {
        val r = CustomFoodForm().applyParsed(ParsedLabel(kcal = null, protein = null, carbs = null, fat = null, servingGrams = 30f, servingUnit = null), "porție")
        assertEquals("porție", r.values[FormField.SERVING_UNIT]); assertEquals("30", r.values[FormField.SERVING_GRAMS])
        assertEquals(setOf(FormField.SERVING_UNIT, FormField.SERVING_GRAMS), r.autoFilled)
        // does not overwrite a typed unit
        val t = form(FormField.SERVING_UNIT to "bol").applyParsed(ParsedLabel(kcal = null, protein = null, carbs = null, fat = null, servingGrams = 30f, servingUnit = null), "porție")
        assertEquals("bol", t.values[FormField.SERVING_UNIT])
        assertEquals(setOf(FormField.SERVING_GRAMS), t.autoFilled)
    }

    @Test fun applyParsed_overwrite_replacesNonNullAndMarks() {
        val start = form(FormField.KCAL to "300", FormField.FAT to "20", FormField.PROTEIN to "5", FormField.SERVING_UNIT to "felie", FormField.SERVING_GRAMS to "40")
        val r = start.applyParsed(ParsedLabel(kcal = 452f, fat = 17f, protein = null, carbs = 60f, servingGrams = 30f, servingUnit = null), "porție", overwrite = true)
        assertEquals("452", r.values[FormField.KCAL])
        assertEquals("17", r.values[FormField.FAT])
        assertEquals("60", r.values[FormField.CARBS])
        assertEquals("5", r.values[FormField.PROTEIN]) // null in the label: kept
        assertEquals("30", r.values[FormField.SERVING_GRAMS])
        assertEquals("felie", r.values[FormField.SERVING_UNIT]) // generic default never replaces a named unit
        assertEquals(setOf(FormField.KCAL, FormField.FAT, FormField.CARBS, FormField.SERVING_GRAMS), r.autoFilled)
        val named = start.applyParsed(ParsedLabel(kcal = null, fat = null, protein = null, carbs = null, servingGrams = 12f, servingUnit = "biscuit"), "porție", overwrite = true)
        assertEquals("biscuit", named.values[FormField.SERVING_UNIT])
    }

    @Test fun applyParsed_defaultIsNoOverwrite() {
        val r = form(FormField.KCAL to "300").applyParsed(ParsedLabel(kcal = 452f, fat = null, protein = null, carbs = null, servingGrams = null, servingUnit = null), "porție")
        assertEquals("300", r.values[FormField.KCAL])
        assertTrue(r.autoFilled.isEmpty())
    }

    @Test fun edit_clearsAutoFilledMark() {
        val r = CustomFoodForm().applyParsed(ParsedLabel(kcal = 452f, protein = null, carbs = null, fat = null, servingGrams = null, servingUnit = null), "porție")
        assertTrue(FormField.KCAL in r.autoFilled)
        assertFalse(FormField.KCAL in r.edit(FormField.KCAL, "450").autoFilled)
    }

    @Test fun fromDto_and_prefilled() {
        val f = CustomFormFromDto()
        assertEquals("Iaurt", f.values[FormField.NAME]); assertEquals("5.5", f.values[FormField.PROTEIN])
        assertEquals("felie", f.values[FormField.SERVING_UNIT]); assertEquals("30", f.values[FormField.SERVING_GRAMS])
        assertTrue(f.autoFilled.isEmpty())
        val p = CustomFoodForm.prefilled("12345678", "Lapte")
        assertEquals("12345678", p.values[FormField.BARCODE]); assertEquals("Lapte", p.values[FormField.NAME])
        assertTrue(CustomFoodForm.prefilled(null, null).values.isEmpty())
    }

    private fun CustomFormFromDto() = CustomFoodForm.fromDto(
        CustomFoodDto(1, "custom-1", "Iaurt", null, null, 100f, 5.5f, 10f, 4f, ServingDto("felie", 30f)))
}
