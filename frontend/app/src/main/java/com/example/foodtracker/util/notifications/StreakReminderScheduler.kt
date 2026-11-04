package com.example.foodtracker.util.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.foodtracker.model.AppSettings
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object StreakReminderScheduler {
    const val UNIQUE_WORK = "streak_reminder"
    const val NOTIFICATION_ID = 11

    /** Diff-based, like [MealReminderScheduler.sync]: touches WorkManager only when the time changed. */
    suspend fun sync(context: Context, settings: AppSettings) {
        val store = ScheduledReminderStore(context.reminderScheduleDataStore)
        val desired = desiredStreakMinute(settings)
        val wm = WorkManager.getInstance(context)
        when (val action = reconcileStreak(desired, store.readStreak())) {
            is ReminderAction.Keep -> enqueue(wm, action.minuteOfDay, ExistingPeriodicWorkPolicy.KEEP)
            is ReminderAction.Schedule ->
                enqueue(wm, action.minuteOfDay, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
            ReminderAction.Cancel -> wm.cancelUniqueWork(UNIQUE_WORK)
        }
        store.writeStreak(desired)
    }

    private fun enqueue(wm: WorkManager, minuteOfDay: Int, policy: ExistingPeriodicWorkPolicy) {
        val request = PeriodicWorkRequestBuilder<StreakReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(
                MealReminderScheduler.nextOccurrenceMillis(minuteOfDay, LocalDateTime.now()),
                TimeUnit.MILLISECONDS,
            )
            .addTag(MealReminderScheduler.TAG)
            .build()
        wm.enqueueUniquePeriodicWork(UNIQUE_WORK, policy, request)
    }
}
