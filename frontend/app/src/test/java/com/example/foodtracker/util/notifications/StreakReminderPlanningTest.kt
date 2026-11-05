package com.example.foodtracker.util.notifications

import com.example.foodtracker.data.StreakSnapshot
import com.example.foodtracker.model.AppSettings
import com.example.foodtracker.model.MealReminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StreakReminderPlanningTest {

    private val today = LocalDate.of(2026, 11, 4)
    private val yesterday = today.minusDays(1)

    @Test fun desiredStreakMinuteDefaults() {
        assertEquals(1200, desiredStreakMinute(AppSettings()))
        assertNull(desiredStreakMinute(AppSettings(notificationsEnabled = false)))
        assertNull(desiredStreakMinute(AppSettings(streakReminder = MealReminder(enabled = false, minuteOfDay = 1200))))
    }

    @Test fun reconcile_keep() = assertEquals(ReminderAction.Keep(1200), reconcileStreak(1200, 1200))

    @Test fun reconcile_schedule() {
        assertEquals(ReminderAction.Schedule(1200), reconcileStreak(1200, null))
        assertEquals(ReminderAction.Schedule(1100), reconcileStreak(1100, 1200))
    }

    @Test fun reconcile_cancel() = assertEquals(ReminderAction.Cancel, reconcileStreak(null, 1200))

    @Test fun noSnapshotNoReminder() = assertFalse(shouldSendStreakReminder(null, today, false))

    @Test fun zeroStreakNoReminder() =
        assertFalse(shouldSendStreakReminder(StreakSnapshot(0, today, null), today, false))

    @Test fun loggedTodayNoReminder() =
        assertFalse(shouldSendStreakReminder(StreakSnapshot(5, today, today), today, false))

    @Test fun freshSnapshotNotLoggedSends() =
        assertTrue(shouldSendStreakReminder(StreakSnapshot(5, today, yesterday), today, false))

    @Test fun yesterdaySnapshotLoggedYesterdaySends() =
        assertTrue(shouldSendStreakReminder(StreakSnapshot(5, yesterday, yesterday), today, false))

    @Test fun yesterdaySnapshotNotLoggedYesterdaySilent() =
        assertFalse(shouldSendStreakReminder(StreakSnapshot(5, yesterday, today.minusDays(2)), today, false))

    @Test fun localLogTodaySuppressesReminder() =
        assertFalse(shouldSendStreakReminder(StreakSnapshot(5, today, yesterday), today, true))

    @Test fun staleSnapshotSilent() {
        val old = LocalDate.of(2026, 11, 1)
        assertFalse(shouldSendStreakReminder(StreakSnapshot(5, old, old), today, false))
    }
}
