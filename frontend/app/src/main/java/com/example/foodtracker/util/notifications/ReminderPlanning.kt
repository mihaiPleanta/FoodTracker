package com.example.foodtracker.util.notifications

import com.example.foodtracker.model.AppSettings
import com.example.foodtracker.model.MealReminder
import com.example.foodtracker.util.notifications.MealReminderScheduler.Meal

/** minuteOfDay scheduled per meal; `null` = not scheduled. Always holds all four meals. */
typealias ReminderSchedule = Map<Meal, Int?>

sealed interface ReminderAction {
    /** Already scheduled at this time — leave the existing work alone. */
    data class Keep(val minuteOfDay: Int) : ReminderAction
    /** Time changed (or nothing scheduled) — replace the work. */
    data class Schedule(val minuteOfDay: Int) : ReminderAction
    data object Cancel : ReminderAction
}

fun desiredSchedule(settings: AppSettings): ReminderSchedule =
    Meal.values().associateWith { meal ->
        val reminder = meal.reminderOf(settings)
        if (settings.notificationsEnabled && reminder.enabled) reminder.minuteOfDay else null
    }

/**
 * What the pre-configurable version left in WorkManager: the default hours when the master
 * switch was on, nothing otherwise. Used when no schedule has been persisted yet.
 */
fun assumedLegacySchedule(settings: AppSettings): ReminderSchedule =
    if (settings.notificationsEnabled) desiredSchedule(AppSettings())
    else Meal.values().associateWith { null }

fun reconcile(desired: ReminderSchedule, scheduled: ReminderSchedule): Map<Meal, ReminderAction> =
    Meal.values().associateWith { meal ->
        val want = desired[meal]
        when {
            want == null              -> ReminderAction.Cancel
            want == scheduled[meal]   -> ReminderAction.Keep(want)
            else                      -> ReminderAction.Schedule(want)
        }
    }

fun AppSettings.withReminder(meal: Meal, reminder: MealReminder): AppSettings = when (meal) {
    Meal.BREAKFAST -> copy(breakfastReminder = reminder)
    Meal.LUNCH     -> copy(lunchReminder = reminder)
    Meal.SNACKS    -> copy(snacksReminder = reminder)
    Meal.DINNER    -> copy(dinnerReminder = reminder)
}
