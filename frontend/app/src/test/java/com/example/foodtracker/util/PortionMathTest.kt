package com.example.foodtracker.util

import com.example.foodtracker.model.PortionUnit
import com.example.foodtracker.model.UnitSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PortionMathTest {
    @Test fun `gramsFor rounds and floors at 1`() {
        assertEquals(14, gramsFor(2f, 7f))
        assertEquals(32, gramsFor(1.5f, 21f))
        assertEquals(1, gramsFor(0.5f, 0.4f))
    }

    @Test fun `parseQty accepts comma and dot`() {
        assertEquals(1.5f, parseQty("1,5"))
        assertEquals(1.5f, parseQty("1.5"))
        assertEquals(0.5f, parseQty(",5"))
        assertNull(parseQty("abc"))
        assertNull(parseQty(""))
        assertNull(parseQty("0"))
        assertNull(parseQty("-1"))
        assertNull(parseQty("1000"))
    }

    @Test fun `stepQty floors at half`() {
        assertEquals(0.5f, stepQty(0.5f, -0.5f))
        assertEquals(1.5f, stepQty(1f, 0.5f))
    }

    @Test fun `formatQty handles half, integers and separator`() {
        assertEquals("½", formatQty(0.5f, ','))
        assertEquals("2", formatQty(2f, ','))
        assertEquals("1,5", formatQty(1.5f, ','))
        assertEquals("1.5", formatQty(1.5f, '.'))
    }

    @Test fun `pluralCount`() {
        assertEquals(1, pluralCount(0.5f))
        assertEquals(1, pluralCount(1f))
        assertEquals(2, pluralCount(1.5f))
        assertEquals(20, pluralCount(20f))
    }

    @Test fun `unitPluralRes covers standard units only`() {
        assertNotNull(unitPluralRes("tsp"))
        assertNull(unitPluralRes("Bolul meu"))
        STANDARD_UNITS.forEach { assertNotNull(it, unitPluralRes(it)) }
        assertEquals(14, STANDARD_UNITS.size)
    }

    @Test fun `mergeLoggedUnit adds missing unit as USER`() {
        val units = listOf(PortionUnit("tsp", 5f, UnitSource.ESTIMATE))
        val merged = mergeLoggedUnit(units, PortionChoice(28, "felie mea", 2f))
        assertEquals(2, merged.size)
        assertEquals(PortionUnit("felie mea", 14f, UnitSource.USER), merged.last())
    }

    @Test fun `mergeLoggedUnit leaves list when present or unit null`() {
        val units = listOf(PortionUnit("tsp", 5f, UnitSource.ESTIMATE))
        assertEquals(units, mergeLoggedUnit(units, PortionChoice(14, "tsp", 2f)))
        assertEquals(units, mergeLoggedUnit(units, PortionChoice(30, null, null)))
    }
}
