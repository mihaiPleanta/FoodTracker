package com.example.foodtracker

import com.example.foodtracker.ui.generateCalendarDays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CalendarLocalizationTest {

    @Test
    fun generateCalendarDays_localizesWeekdayAbbreviations() {
        val en = generateCalendarDays(Locale.ENGLISH)
        val ro = generateCalendarDays(Locale("ro"))

        assertEquals(en.size, ro.size)
        // The localized weekday abbreviations must differ between English and
        // Romanian for at least one day — proving the locale is actually applied
        // and not hardcoded to English (regression for the "SUN/MON/TUE" bug).
        assertTrue(
            "Romanian weekday abbreviations should differ from English",
            en.indices.any { en[it].dayOfWeekShort != ro[it].dayOfWeekShort }
        )
    }
}
