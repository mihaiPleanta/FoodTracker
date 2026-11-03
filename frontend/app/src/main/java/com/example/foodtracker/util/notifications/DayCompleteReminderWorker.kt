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
import com.example.foodtracker.data.settingsDataStore
import com.example.foodtracker.model.AppLanguage
import kotlinx.coroutines.flow.first
import java.util.Locale

class DayCompleteReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val settings = DataStoreSettingsRepository(applicationContext.settingsDataStore)
            .settings.first()
        // Safety net: a stale work may outlive a switch turned off in Settings.
        if (desiredDayCompleteMinute(settings) == null) return Result.success()
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            return Result.success()
        }

        val languageTag = if (settings.language == AppLanguage.ENGLISH) "en" else "ro"
        val config = Configuration(applicationContext.resources.configuration).apply {
            setLocale(Locale(languageTag))
        }
        val localizedCtx = applicationContext.createConfigurationContext(config)

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_TODAY, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            DayCompleteReminderScheduler.NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.MEAL_REMINDERS)
            .setSmallIcon(R.drawable.ic_meal_reminder)
            .setContentTitle(localizedCtx.getString(R.string.notif_day_complete_title))
            .setContentText(localizedCtx.getString(R.string.notif_day_complete_body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(applicationContext)
                .notify(DayCompleteReminderScheduler.NOTIFICATION_ID, notification)
        }
        return Result.success()
    }

    companion object {
        const val EXTRA_OPEN_TODAY = "open_today"
    }
}
