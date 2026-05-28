package com.example.foodtracker.util.notifications

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime

class MealReminderSchedulerTest {

    @Test
    fun nextOccurrence_targetLaterToday_returnsTodayDelay() {
        val now = LocalDateTime.of(2026, 5, 28, 6, 0)
        val delay = MealReminderScheduler.nextOccurrenceMillis(targetHour = 8, now = now)
        assertEquals(Duration.ofHours(2).toMillis(), delay)
    }

    @Test
    fun nextOccurrence_targetEarlierToday_returnsTomorrowDelay() {
        val now = LocalDateTime.of(2026, 5, 28, 14, 0)
        val delay = MealReminderScheduler.nextOccurrenceMillis(targetHour = 8, now = now)
        assertEquals(Duration.ofHours(18).toMillis(), delay)
    }

    @Test
    fun nextOccurrence_targetExactlyNow_returnsTomorrowDelay() {
        // isBefore() is strict — same instant rolls to tomorrow.
        val now = LocalDateTime.of(2026, 5, 28, 8, 0)
        val delay = MealReminderScheduler.nextOccurrenceMillis(targetHour = 8, now = now)
        assertEquals(Duration.ofHours(24).toMillis(), delay)
    }

    @Test
    fun nextOccurrence_targetOneMillisecondAway_returnsTodayDelay() {
        val now = LocalDateTime.of(2026, 5, 28, 7, 59, 59, 999_000_000)
        val delay = MealReminderScheduler.nextOccurrenceMillis(targetHour = 8, now = now)
        assertEquals(1L, delay)
    }
}
