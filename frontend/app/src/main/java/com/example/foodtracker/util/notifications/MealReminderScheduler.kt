package com.example.foodtracker.util.notifications

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

object MealReminderScheduler {

    internal const val TAG = "meal_reminder"

    enum class Meal(
        val key: String,          // "Breakfast" — match cu mealList()/FoodLogCreateDto
        val displayName: String,  // afișat în notification title
        val emoji: String,        // pentru deep-link payload + notif body
        val accentHex: String,    // 6-char hex (match cu "%06X" din MainActivity)
        val targetHour: Int,
        val notificationId: Int,
        val uniqueWorkName: String,
    ) {
        BREAKFAST("Breakfast", "micul dejun", "🍳", "FFD600", 8,  1, "meal_reminder_breakfast"),
        LUNCH    ("Lunch",     "prânz",       "🥗", "00E676", 13, 2, "meal_reminder_lunch"),
        SNACKS   ("Snacks",    "gustare",     "🍎", "FF6D00", 16, 3, "meal_reminder_snacks"),
        DINNER   ("Dinner",    "cină",        "🍝", "448AFF", 19, 4, "meal_reminder_dinner"),
    }

    /** Visible for testing — pure function. */
    internal fun nextOccurrenceMillis(targetHour: Int, now: LocalDateTime): Long {
        val todayAtTarget = now.toLocalDate().atTime(LocalTime.of(targetHour, 0))
        val target = if (now.isBefore(todayAtTarget)) todayAtTarget else todayAtTarget.plusDays(1)
        return Duration.between(now, target).toMillis()
    }
}
