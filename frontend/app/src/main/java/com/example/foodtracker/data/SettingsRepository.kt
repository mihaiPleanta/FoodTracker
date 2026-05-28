package com.example.foodtracker.data

import com.example.foodtracker.model.AppSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(settings: AppSettings)
}
