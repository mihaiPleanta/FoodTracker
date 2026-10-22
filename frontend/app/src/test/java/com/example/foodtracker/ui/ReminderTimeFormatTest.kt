package com.example.foodtracker.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderTimeFormatTest {

    @Test
    fun format_24h_padsHourAndMinute() {
        assertEquals("08:05", formatMinuteOfDay(485, is24Hour = true))
    }

    @Test
    fun format_12h_morning() {
        assertEquals("8:05 AM", formatMinuteOfDay(485, is24Hour = false))
    }

    @Test
    fun format_12h_noon() {
        assertEquals("12:00 PM", formatMinuteOfDay(720, is24Hour = false))
    }

    @Test
    fun format_12h_midnight() {
        assertEquals("12:30 AM", formatMinuteOfDay(30, is24Hour = false))
    }
}
