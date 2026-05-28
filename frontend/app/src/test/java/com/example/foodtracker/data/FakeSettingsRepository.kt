package com.example.foodtracker.data

import com.example.foodtracker.model.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSettingsRepository(
    initial: AppSettings = AppSettings()
) : SettingsRepository {
    private val _settings = MutableStateFlow(initial)
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val updates = mutableListOf<AppSettings>()

    override suspend fun update(settings: AppSettings) {
        updates += settings
        _settings.value = settings
    }

    /** Test-only: forces a value without going through update(). */
    fun emit(settings: AppSettings) {
        _settings.value = settings
    }
}
