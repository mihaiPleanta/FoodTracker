package com.example.foodtracker.util.notifications

import com.example.foodtracker.data.StreakSnapshot
import com.example.foodtracker.data.isSnapshotPlausible
import com.example.foodtracker.model.AppSettings
import java.time.LocalDate

/** Minute of day for the evening streak reminder; `null` = should not be scheduled. */
fun desiredStreakMinute(settings: AppSettings): Int? =
    if (settings.notificationsEnabled && settings.streakReminder.enabled) {
        settings.streakReminder.minuteOfDay
    } else null

fun reconcileStreak(desired: Int?, scheduled: Int?): ReminderAction = when {
    desired == null      -> ReminderAction.Cancel
    desired == scheduled -> ReminderAction.Keep(desired)
    else                 -> ReminderAction.Schedule(desired)
}

/**
 * Whether the evening reminder should fire, judged from the last server-confirmed snapshot.
 * A snapshot from yesterday only counts if yesterday was logged (streak still alive today);
 * anything older is stale and stays silent.
 */
fun shouldSendStreakReminder(
    snapshot: StreakSnapshot?,
    today: LocalDate,
    todayLoggedLocally: Boolean,
): Boolean {
    if (snapshot == null || snapshot.current <= 0) return false
    if (todayLoggedLocally) return false // includes food logged offline and not yet synced
    if (snapshot.lastLoggedDate == today) return false
    return isSnapshotPlausible(snapshot, today)
}
