package com.example.foodtracker.util.notifications

import com.example.foodtracker.model.AppSettings
import com.example.foodtracker.model.MealReminder
import com.example.foodtracker.util.notifications.MealReminderScheduler.Meal
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderPlanningTest {

    private val defaults = mapOf(
        Meal.BREAKFAST to 480, Meal.LUNCH to 780, Meal.SNACKS to 960, Meal.DINNER to 1140,
    )

    @Test
    fun desired_defaults_allMealsAtDefaultTimes() {
        assertEquals(defaults, desiredSchedule(AppSettings()))
    }

    @Test
    fun desired_masterOff_allNull() {
        val desired = desiredSchedule(AppSettings(notificationsEnabled = false))
        assertEquals(Meal.values().associateWith { null }, desired)
    }

    @Test
    fun desired_mealDisabled_nullOnlyForThatMeal() {
        val settings = AppSettings(snacksReminder = MealReminder(enabled = false, minuteOfDay = 960))
        assertEquals(defaults + (Meal.SNACKS to null), desiredSchedule(settings))
    }

    @Test
    fun reconcile_noPersistedState_masterOn_keepsDefaults() {
        val actions = reconcile(desiredSchedule(AppSettings()), assumedLegacySchedule(AppSettings()))
        assertEquals(defaults.mapValues { ReminderAction.Keep(it.value) }, actions)
    }

    @Test
    fun reconcile_noPersistedState_masterOff_cancelsAll() {
        val settings = AppSettings(notificationsEnabled = false)
        val actions = reconcile(desiredSchedule(settings), assumedLegacySchedule(settings))
        assertEquals(Meal.values().associateWith { ReminderAction.Cancel }, actions)
    }

    @Test
    fun reconcile_persistedDiffers_schedules() {
        val desired = defaults + (Meal.LUNCH to 750)
        val actions = reconcile(desired, defaults)
        assertEquals(ReminderAction.Schedule(750), actions[Meal.LUNCH])
        assertEquals(ReminderAction.Keep(480), actions[Meal.BREAKFAST])
        assertEquals(ReminderAction.Keep(960), actions[Meal.SNACKS])
        assertEquals(ReminderAction.Keep(1140), actions[Meal.DINNER])
    }

    @Test
    fun reconcile_masterOff_cancelsAll() {
        val desired = desiredSchedule(AppSettings(notificationsEnabled = false))
        val actions = reconcile(desired, defaults)
        assertEquals(Meal.values().associateWith { ReminderAction.Cancel }, actions)
    }

    @Test
    fun reconcile_masterOffThenTimeChangedThenOn_schedulesNewTime() {
        val afterCancel = Meal.values().associateWith { null }
        val settings = AppSettings(lunchReminder = MealReminder(enabled = true, minuteOfDay = 750))
        val actions = reconcile(desiredSchedule(settings), afterCancel)
        assertEquals(ReminderAction.Schedule(750), actions[Meal.LUNCH])
        assertEquals(ReminderAction.Schedule(480), actions[Meal.BREAKFAST])
        assertEquals(ReminderAction.Schedule(960), actions[Meal.SNACKS])
        assertEquals(ReminderAction.Schedule(1140), actions[Meal.DINNER])
    }
}
