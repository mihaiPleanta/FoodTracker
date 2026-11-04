package com.example.foodtracker.util.notifications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.foodtracker.MainActivity
import com.example.foodtracker.R
import com.example.foodtracker.data.DataStoreSettingsRepository
import com.example.foodtracker.data.StreakSnapshotStore
import com.example.foodtracker.data.settingsDataStore
import com.example.foodtracker.data.streakSnapshotDataStore
import com.example.foodtracker.model.AppLanguage
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.Locale

class StreakReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val settings = DataStoreSettingsRepository(applicationContext.settingsDataStore)
            .settings.first()
        // Safety net: a stale work may outlive a switch turned off in Settings.
        if (desiredStreakMinute(settings) == null) return Result.success()
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            return Result.success()
        }

        val snapshot = StreakSnapshotStore(applicationContext.streakSnapshotDataStore).read()
        if (snapshot == null || !shouldSendStreakReminder(snapshot, LocalDate.now())) return Result.success()

        val languageTag = if (settings.language == AppLanguage.ENGLISH) "en" else "ro"
        val config = Configuration(applicationContext.resources.configuration).apply {
            setLocale(Locale(languageTag))
        }
        val localizedCtx = applicationContext.createConfigurationContext(config)

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(DayCompleteReminderWorker.EXTRA_OPEN_TODAY, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            StreakReminderScheduler.NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.MEAL_REMINDERS)
            .setSmallIcon(R.drawable.ic_meal_reminder)
            .setContentTitle(localizedCtx.getString(R.string.notif_streak_title, snapshot.current))
            .setContentText(localizedCtx.getString(R.string.notif_streak_body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(applicationContext)
                .notify(StreakReminderScheduler.NOTIFICATION_ID, notification)
        }
        return Result.success()
    }
}
