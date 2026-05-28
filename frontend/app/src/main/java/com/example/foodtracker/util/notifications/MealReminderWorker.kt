package com.example.foodtracker.util.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.foodtracker.MainActivity
import com.example.foodtracker.R
import com.example.foodtracker.data.MealLogTracker
import com.example.foodtracker.data.mealLogTrackerDataStore
import java.net.URLEncoder
import java.time.LocalDate

class MealReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val mealName = inputData.getString(KEY_MEAL) ?: return Result.success()
        val meal = runCatching {
            MealReminderScheduler.Meal.valueOf(mealName)
        }.getOrNull() ?: return Result.success()

        val tracker = MealLogTracker(applicationContext.mealLogTrackerDataStore)
        if (tracker.wasLogged(LocalDate.now(), meal.key)) return Result.success()

        // User may have revoked notifications via system settings — respect that on every API level.
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            return Result.success()
        }

        val encodedIcon = URLEncoder.encode(meal.emoji, "UTF-8")
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_MEAL_NAME, meal.key)
            putExtra(EXTRA_MEAL_ICON, encodedIcon)
            putExtra(EXTRA_MEAL_ACCENT, meal.accentHex)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            meal.notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(
            applicationContext,
            NotificationChannels.MEAL_REMINDERS,
        )
            .setSmallIcon(R.drawable.ic_meal_reminder)
            .setContentTitle("Timpul pentru ${meal.displayName}")
            .setContentText("Loghează ce ai mâncat ${meal.emoji}")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(applicationContext)
                .notify(meal.notificationId, notification)
        }
        return Result.success()
    }

    companion object {
        const val KEY_MEAL = "meal_name"
        const val EXTRA_MEAL_NAME = "extra_meal_name"
        const val EXTRA_MEAL_ICON = "extra_meal_icon"
        const val EXTRA_MEAL_ACCENT = "extra_meal_accent"
    }
}
