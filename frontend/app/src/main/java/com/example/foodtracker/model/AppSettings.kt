package com.example.foodtracker.model

enum class AppLanguage { ROMANIAN, ENGLISH }

data class AppSettings(
    val notificationsEnabled: Boolean = true,
    val isDarkTheme: Boolean = true,
    val language: AppLanguage = AppLanguage.ROMANIAN
)
