package com.example.foodtracker.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HydrationSettingsHelpersTest {

    @Test fun stepManualGoal_stepsAndRounds() {
        assertEquals(3.1f, stepManualGoal(3.0f, 0.1f))
        assertEquals(2.9f, stepManualGoal(3.0f, -0.1f))
    }

    @Test fun stepManualGoal_noFloatDrift() {
        var v = 1.0f
        repeat(20) { v = stepManualGoal(v, 0.1f) }
        assertEquals(3.0f, v)
    }

    @Test fun stepManualGoal_clamps() {
        assertEquals(1.0f, stepManualGoal(1.0f, -0.1f))
        assertEquals(6.0f, stepManualGoal(6.0f, 0.1f))
    }

    @Test fun formatLiters_usesDotAndOneDecimal() {
        assertEquals("3.5L", formatLiters(3.5f))
    }

    @Test fun formatPartLiters_keepsTwoDecimalsWhenNeeded() {
        assertEquals("2.45L", formatPartLiters(2.45f))
        assertEquals("0.5L", formatPartLiters(0.5f))
    }

    @Test fun formatKg_dropsWholeDecimal() {
        assertEquals("70", formatKg(70.0f))
        assertEquals("78.1", formatKg(78.1f))
    }
}
