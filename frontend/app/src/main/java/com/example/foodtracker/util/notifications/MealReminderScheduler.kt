package com.example.foodtracker.util.notifications

import android.content.Context
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
        val displayName: String,
        val emoji: String,
        val accentHex: String,
        val targetHour: Int,
        val notificationId: Int,
        val uniqueWorkName: String,
    ) {
        BREAKFAST("Breakfast", "micul dejun", "🍳", "FFD600", 8,  1, "meal_reminder_breakfast"),
        LUNCH    ("Lunch",     "prânz",       "🥗", "00E676", 13, 2, "meal_reminder_lunch"),
        SNACKS   ("Snacks",    "gustare",     "🍎", "FF6D00", 16, 3, "meal_reminder_snacks"),
        DINNER   ("Dinner",    "cină",        "🍝", "448AFF", 19, 4, "meal_reminder_dinner"),
    }

    /** Enqueue four unique periodic work requests, idempotent thanks to KEEP policy. */
    fun scheduleAll(context: Context) {
        val wm = WorkManager.getInstance(context)
        val now = LocalDateTime.now()
        Meal.values().forEach { meal ->
            val delayMillis = nextOccurrenceMillis(meal.targetHour, now)
            // Folosim overload-urile (long, TimeUnit) — variantele cu java.time.Duration
            // din WorkManager cer API 26+ și nu sunt acoperite de desugaring.
            val request = PeriodicWorkRequestBuilder<MealReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(MealReminderWorker.KEY_MEAL to meal.name))
                .addTag(TAG)
                .build()
            wm.enqueueUniquePeriodicWork(
                meal.uniqueWorkName,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
    }

    internal fun nextOccurrenceMillis(targetHour: Int, now: LocalDateTime): Long {
        val todayAtTarget = now.toLocalDate().atTime(LocalTime.of(targetHour, 0))
        val target = if (now.isBefore(todayAtTarget)) todayAtTarget else todayAtTarget.plusDays(1)
        return Duration.between(now, target).toMillis()
    }
}
