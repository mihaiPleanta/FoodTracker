package com.example.foodtracker.util.label

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelParserTest {

    private fun assertValue(expected: Float, actual: Float?, field: String) {
        assertEquals(field, expected, actual ?: error("$field was null"), 0.001f)
    }

    private fun assertMacros(
        label: ParsedLabel,
        kcal: Float,
        fat: Float,
        carbs: Float,
        protein: Float,
    ) {
        assertValue(kcal, label.kcal, "kcal")
        assertValue(fat, label.fat, "fat")
        assertValue(carbs, label.carbs, "carbs")
        assertValue(protein, label.protein, "protein")
    }

    @Test
    fun singleColumnRo_readsMainValues_andSkipsOfWhichRows() {
        val label = LabelParser.parse(LabelFixtures.singleColumnRo)
        assertMacros(label, kcal = 452f, fat = 17f, carbs = 68f, protein = 6.1f)
        // "per 100 g" is the reference column, not a serving.
        assertNull(label.servingGrams)
        assertNull(label.servingUnit)
    }

    @Test
    fun per100AndServing_takesThe100gColumn_andReadsServing() {
        val label = LabelParser.parse(LabelFixtures.per100AndServing)
        assertMacros(label, kcal = 452f, fat = 17f, carbs = 68f, protein = 6.1f)
        assertValue(30f, label.servingGrams, "servingGrams")
        // Generic portion: no unit name, the UI supplies a localized default (ruling R4).
        assertNull(label.servingUnit)
    }

    @Test
    fun missing100gCell_neverBorrowsThePortionValue() {
        val label = LabelParser.parse(LabelFixtures.missing100gCell)
        assertNull(label.protein)
        assertMacros(label.copy(protein = 0f), kcal = 452f, fat = 17f, carbs = 68f, protein = 0f)
    }

    @Test
    fun per100AndRi_ignoresPercentages_andTheRiFootnote() {
        val label = LabelParser.parse(LabelFixtures.per100AndRi)
        assertMacros(label, kcal = 452f, fat = 17f, carbs = 68f, protein = 6.1f)
        assertNull(label.servingGrams)
    }

    @Test
    fun kjOnly_convertsToKcal() {
        val label = LabelParser.parse(LabelFixtures.kjOnly)
        assertMacros(label, kcal = 250f, fat = 9f, carbs = 35f, protein = 4.5f)
    }

    @Test
    fun kjThousandsSeparator_neverZero() {
        for (fixture in listOf(
            LabelFixtures.kjThousandsDot, LabelFixtures.kjThousandsComma,
            LabelFixtures.kjThousandsSpace, LabelFixtures.kjThousandsNarrowSpace,
        )) {
            val label = LabelParser.parse(fixture)
            assertTrue("kcal = ${label.kcal}", label.kcal == null || label.kcal == 250f)
            assertEquals(9f, label.fat)
        }
    }

    @Test
    fun kjOnly_belowTenKcal_isNull() {
        val label = LabelParser.parse(LabelFixtures.kjOnlyTiny)
        assertNull(label.kcal)
        assertEquals(9f, label.fat)
    }

    @Test
    fun lessThan_readsTheBound() {
        val label = LabelParser.parse(LabelFixtures.lessThan)
        assertMacros(label, kcal = 4f, fat = 0.5f, carbs = 0.9f, protein = 0f)
    }

    @Test
    fun englishLabel_readsValues_andIgnoresSugars() {
        val label = LabelParser.parse(LabelFixtures.englishLabel)
        assertMacros(label, kcal = 250f, fat = 9f, carbs = 35f, protein = 4.5f)
        assertValue(30f, label.servingGrams, "servingGrams")
        assertNull(label.servingUnit)
    }

    @Test
    fun ocrNoise_fixesLetterDigitConfusions() {
        val label = LabelParser.parse(LabelFixtures.ocrNoise)
        assertMacros(label, kcal = 375f, fat = 17f, carbs = 42.3f, protein = 10.5f)
    }

    @Test
    fun energySplitAcrossRows_findsKcalOnTheNextRow() {
        val label = LabelParser.parse(LabelFixtures.energySplitAcrossRows)
        assertMacros(label, kcal = 452f, fat = 17f, carbs = 68f, protein = 6.1f)
    }

    @Test
    fun pieceServing_readsPieceNameAndWeight() {
        val label = LabelParser.parse(LabelFixtures.pieceServing)
        assertMacros(label, kcal = 480f, fat = 22f, carbs = 63f, protein = 7f)
        assertValue(12f, label.servingGrams, "servingGrams")
        assertEquals("biscuit", label.servingUnit)
    }

    @Test
    fun implausible_valueBecomesNull_othersKept() {
        val label = LabelParser.parse(LabelFixtures.implausible)
        assertNull(label.protein)
        assertValue(370f, label.kcal, "kcal")
        assertValue(12f, label.fat, "fat")
        assertValue(55f, label.carbs, "carbs")
    }

    @Test
    fun macroSumOver100_dropsAllMacros_keepsKcal() {
        val label = LabelParser.parse(LabelFixtures.macroSumOver100)
        assertNull(label.protein)
        assertNull(label.carbs)
        assertNull(label.fat)
        assertValue(452f, label.kcal, "kcal")
    }

    @Test
    fun ingredientsFatAboveTable_usesTheTableNotTheIngredients() {
        val label = LabelParser.parse(LabelFixtures.ingredientsFatAboveTable)
        assertMacros(label, kcal = 452f, fat = 17f, carbs = 68f, protein = 6.1f)
    }

    @Test
    fun ingredientsProteinNoHeader_usesTheTableNotTheIngredients() {
        val label = LabelParser.parse(LabelFixtures.ingredientsProteinNoHeader)
        assertMacros(label, kcal = 370f, fat = 12f, carbs = 55f, protein = 6.1f)
    }

    @Test
    fun jamProse100g_isNotMistakenForTheColumnHeader() {
        val label = LabelParser.parse(LabelFixtures.jamProse100g)
        assertMacros(label, kcal = 250f, fat = 0.2f, carbs = 60f, protein = 0.5f)
        assertValue(20f, label.servingGrams, "servingGrams")
        assertNull(label.servingUnit)
    }

    @Test
    fun pieceWithDiacritics_keepsOriginalSpelling() {
        val label = LabelParser.parse(LabelFixtures.pieceWithDiacritics)
        assertValue(20f, label.servingGrams, "servingGrams")
        assertEquals("bucată", label.servingUnit)
    }

    @Test
    fun garbage_isEmpty() {
        val label = LabelParser.parse(LabelFixtures.garbage)
        assertTrue(label.toString(), label.isEmpty)
    }

    @Test
    fun emptyInput_isEmpty() {
        assertTrue(LabelParser.parse(emptyList()).isEmpty)
    }

    @Test
    fun isEmpty_falseWhenAnyFieldSet() {
        assertFalse(ParsedLabel(null, null, null, 1f, null, null).isEmpty)
    }
}
