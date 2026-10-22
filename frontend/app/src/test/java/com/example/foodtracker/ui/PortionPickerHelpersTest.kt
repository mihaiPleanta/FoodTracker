package com.example.foodtracker.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortionPickerHelpersTest {

    @Test
    fun `qtyInputText keeps integers plain and uses the decimal separator`() {
        assertEquals("2", qtyInputText(2f, ','))
        assertEquals("1,5", qtyInputText(1.5f, ','))
        assertEquals("0.5", qtyInputText(0.5f, '.'))
    }

    @Test
    fun `qtyInputText keeps two decimals so an unchanged edit reads back the same value`() {
        assertEquals("1,25", qtyInputText(1.25f, ','))
        assertEquals("0.75", qtyInputText(0.75f, '.'))
        assertEquals("1,3", qtyInputText(1.3f, ','))
        assertEquals(1.25f, com.example.foodtracker.util.parseQty(qtyInputText(1.25f, ',')))
    }

    @Test
    fun `isEditableQty allows digits and at most two decimals`() {
        assertTrue(isEditableQty(""))
        assertTrue(isEditableQty("5000"))
        assertTrue(isEditableQty("1,25"))
        assertTrue(isEditableQty("1."))
        assertFalse(isEditableQty("1,255"))
        assertFalse(isEditableQty("123456"))
        assertFalse(isEditableQty("1,2,3"))
        assertFalse(isEditableQty("-1"))
    }

    @Test
    fun `parseUnitGrams accepts comma or dot and rejects zero or above 5000`() {
        assertEquals(6.5f, parseUnitGrams("6,5"))
        assertEquals(250f, parseUnitGrams(" 250 "))
        assertNull(parseUnitGrams("0"))
        assertNull(parseUnitGrams("5001"))
        assertNull(parseUnitGrams("abc"))
    }

    @Test
    fun `parsePresets reads up to five distinct values sorted ascending`() {
        assertEquals(listOf(42), parsePresets("42"))
        assertEquals(listOf(30, 50, 100), parsePresets("100, 30 50,50"))
        assertNull(parsePresets(""))
        assertNull(parsePresets("1,2,3,4,5,6"))
        assertNull(parsePresets("0, 10"))
        assertNull(parsePresets("10, abc"))
        assertNull(parsePresets("6000"))
    }

    @Test
    fun `isUnitNameTaken compares trimmed and casefolded, including standard keys`() {
        val taken = listOf("Bolul meu", "linguriță")
        assertTrue(isUnitNameTaken(" bolul MEU ", taken))
        assertTrue(isUnitNameTaken("Linguriță", taken))
        assertTrue(isUnitNameTaken("TSP", taken))
        assertFalse(isUnitNameTaken("cana mare", taken))
    }

    @Test
    fun `isValidUnitName requires 1 to 30 characters after trimming`() {
        assertFalse(isValidUnitName("   "))
        assertTrue(isValidUnitName(" a "))
        assertFalse(isValidUnitName("x".repeat(31)))
    }
}
