package com.example.foodtracker.util.notifications

import android.content.Context
import androidx.annotation.StringRes
import com.example.foodtracker.R
import com.example.foodtracker.model.AppSettings
import com.example.foodtracker.model.MealReminder
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object MealReminderScheduler {

    internal const val TAG = "meal_reminder"

    enum class Meal(
        val key: String,
        @StringRes val nameRes: Int,
        val emoji: String,
        val accentHex: String,
        val notificationId: Int,
        val uniqueWorkName: String,
    ) {
        BREAKFAST("Breakfast", R.string.notif_meal_name_breakfast, "🍳", "FFD600", 1, "meal_reminder_breakfast"),
        LUNCH    ("Lunch",     R.string.notif_meal_name_lunch,     "🥗", "00E676", 2, "meal_reminder_lunch"),
        SNACKS   ("Snacks",    R.string.notif_meal_name_snacks,    "🍎", "FF6D00", 3, "meal_reminder_snacks"),
        DINNER   ("Dinner",    R.string.notif_meal_name_dinner,    "🍝", "448AFF", 4, "meal_reminder_dinner");

        fun reminderOf(settings: AppSettings): MealReminder = when (this) {
            BREAKFAST -> settings.breakfastReminder
            LUNCH     -> settings.lunchReminder
            SNACKS    -> settings.snacksReminder
            DINNER    -> settings.dinnerReminder
        }
    }

    /**
     * Aduce WorkManager la programul dorit de [settings]. Compară cu ce s-a programat efectiv
     * (persistat), deci mesele neschimbate rămân neatinse, iar o oră salvată înainte de un
     * process death se aplică la următoarea pornire.
     */
    suspend fun sync(context: Context, settings: AppSettings) {
        val store = ScheduledReminderStore(context.reminderScheduleDataStore)
        val desired = desiredSchedule(settings)
        val scheduled = store.read() ?: assumedLegacySchedule(settings)
        val wm = WorkManager.getInstance(context)
        val now = LocalDateTime.now()
        reconcile(desired, scheduled).forEach { (meal, action) ->
            when (action) {
                is ReminderAction.Keep ->
                    enqueue(wm, meal, action.minuteOfDay, now, ExistingPeriodicWorkPolicy.KEEP)
                is ReminderAction.Schedule ->
                    enqueue(wm, meal, action.minuteOfDay, now, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
                ReminderAction.Cancel -> wm.cancelUniqueWork(meal.uniqueWorkName)
            }
        }
        store.write(desired)
    }

    private fun enqueue(
        wm: WorkManager,
        meal: Meal,
        minuteOfDay: Int,
        now: LocalDateTime,
        policy: ExistingPeriodicWorkPolicy,
    ) {
        // Folosim overload-urile (long, TimeUnit) — variantele cu java.time.Duration
        // din WorkManager cer API 26+ și nu sunt acoperite de desugaring.
        val request = PeriodicWorkRequestBuilder<MealReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(nextOccurrenceMillis(minuteOfDay, now), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(MealReminderWorker.KEY_MEAL to meal.name))
            .addTag(TAG)
            .build()
        wm.enqueueUniquePeriodicWork(meal.uniqueWorkName, policy, request)
    }

    internal fun nextOccurrenceMillis(minuteOfDay: Int, now: LocalDateTime): Long {
        val todayAtTarget = now.toLocalDate().atTime(LocalTime.of(minuteOfDay / 60, minuteOfDay % 60))
        val target = if (now.isBefore(todayAtTarget)) todayAtTarget else todayAtTarget.plusDays(1)
        return Duration.between(now, target).toMillis()
    }
}
