package com.example.foodtracker.ui.stats

import com.example.foodtracker.stats.PeriodKind
import com.example.foodtracker.stats.StatsPeriod
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class PeriodFormatTest {
    @Test fun signedInt_usesExplicitSigns() {
        assertEquals("+400", signedInt(400))
        assertEquals("−300", signedInt(-300))
        assertEquals("0", signedInt(0))
    }

    @Test fun signedKg_localeDecimalAndSign() {
        val ro = Locale.forLanguageTag("ro")
        assertEquals("−0,45", signedKg(-0.45f, ro))
        assertEquals("+0.45", signedKg(0.45f, Locale.ENGLISH))
        assertEquals("0.00", signedKg(0.001f, Locale.ENGLISH))
    }

    @Test fun periodLabel_weekAndMonth() {
        val week = StatsPeriod(PeriodKind.WEEK, LocalDate.of(2026, 10, 26))
        assertEquals("26 Oct – 1 Nov", periodLabel(week, Locale.ENGLISH))
        val month = StatsPeriod(PeriodKind.MONTH, LocalDate.of(2026, 10, 1))
        assertEquals("October 2026", periodLabel(month, Locale.ENGLISH))
    }
}
