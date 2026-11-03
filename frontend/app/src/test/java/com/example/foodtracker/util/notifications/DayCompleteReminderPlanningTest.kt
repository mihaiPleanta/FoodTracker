package com.example.foodtracker.util.notifications

import com.example.foodtracker.model.AppSettings
import com.example.foodtracker.model.MealReminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DayCompleteReminderPlanningTest {

    private val on = AppSettings(dayCompleteReminder = MealReminder(enabled = true, minuteOfDay = 21 * 60 + 30))

    @Test fun offByDefault_isNull() = assertNull(desiredDayCompleteMinute(AppSettings()))

    @Test fun enabled_returnsMinute() = assertEquals(1290, desiredDayCompleteMinute(on))

    @Test fun masterSwitchOff_isNull() =
        assertNull(desiredDayCompleteMinute(on.copy(notificationsEnabled = false)))

    @Test fun reconcile_keep() =
        assertEquals(ReminderAction.Keep(1290), reconcileDayComplete(1290, 1290))

    @Test fun reconcile_scheduleWhenNothingScheduled() =
        assertEquals(ReminderAction.Schedule(1290), reconcileDayComplete(1290, null))

    @Test fun reconcile_scheduleWhenTimeChanged() =
        assertEquals(ReminderAction.Schedule(1200), reconcileDayComplete(1200, 1290))

    @Test fun reconcile_cancel() =
        assertEquals(ReminderAction.Cancel, reconcileDayComplete(null, 1290))
}
