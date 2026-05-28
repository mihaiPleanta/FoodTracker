package com.example.foodtracker.util.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService

object NotificationChannels {
    const val MEAL_REMINDERS = "meal_reminders"

    /** Idempotent — safe to call on every cold start. No-op pre-O. */
    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return
        val channel = NotificationChannel(
            MEAL_REMINDERS,
            "Remindere mese",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Notificări la orele de mese pentru a-ți aminti să logezi"
        }
        manager.createNotificationChannel(channel)
    }
}
