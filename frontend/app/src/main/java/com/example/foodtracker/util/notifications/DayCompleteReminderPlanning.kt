package com.example.foodtracker.util.notifications

import com.example.foodtracker.model.AppSettings

/** Minute of day for the evening "done for today?" reminder; `null` = should not be scheduled. */
fun desiredDayCompleteMinute(settings: AppSettings): Int? =
    if (settings.notificationsEnabled && settings.dayCompleteReminder.enabled) {
        settings.dayCompleteReminder.minuteOfDay
    } else null

fun reconcileDayComplete(desired: Int?, scheduled: Int?): ReminderAction = when {
    desired == null      -> ReminderAction.Cancel
    desired == scheduled -> ReminderAction.Keep(desired)
    else                 -> ReminderAction.Schedule(desired)
}
