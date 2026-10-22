package com.example.foodtracker.model

enum class AppLanguage { ROMANIAN, ENGLISH }

/** Reminder for one meal; time stored as minutes since midnight (0..1439). */
data class MealReminder(
    val enabled: Boolean = true,
    val minuteOfDay: Int
)

data class AppSettings(
    val notificationsEnabled: Boolean = true,
    val isDarkTheme: Boolean = true,
    val language: AppLanguage = AppLanguage.ROMANIAN,
    val breakfastReminder: MealReminder = MealReminder(minuteOfDay = 8 * 60),
    val lunchReminder: MealReminder = MealReminder(minuteOfDay = 13 * 60),
    val snacksReminder: MealReminder = MealReminder(minuteOfDay = 16 * 60),
    val dinnerReminder: MealReminder = MealReminder(minuteOfDay = 19 * 60)
)
