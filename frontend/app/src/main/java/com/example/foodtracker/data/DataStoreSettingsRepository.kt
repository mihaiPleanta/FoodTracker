package com.example.foodtracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.foodtracker.model.AppLanguage
import com.example.foodtracker.model.AppSettings
import com.example.foodtracker.model.MealReminder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

// Extension property la nivel de fișier — singleton DataStore per proces.
// MainActivity îl folosește pentru a obține un DataStore<Preferences> pe care
// îl pasează la DataStoreSettingsRepository.
internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_settings"
)

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    private object Keys {
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val IS_DARK_THEME         = booleanPreferencesKey("is_dark_theme")
        val LANGUAGE              = stringPreferencesKey("language")
    }

    /** Pair of keys per meal: `reminder_<meal>_enabled` + `reminder_<meal>_minutes`. */
    private class ReminderKeys(meal: String) {
        val enabled = booleanPreferencesKey("reminder_${meal}_enabled")
        val minutes = intPreferencesKey("reminder_${meal}_minutes")
    }

    private val breakfastKeys = ReminderKeys("breakfast")
    private val lunchKeys     = ReminderKeys("lunch")
    private val snacksKeys    = ReminderKeys("snacks")
    private val dinnerKeys    = ReminderKeys("dinner")

    private val defaults = AppSettings()

    private fun Preferences.reminder(keys: ReminderKeys, default: MealReminder) = MealReminder(
        enabled     = this[keys.enabled] ?: default.enabled,
        minuteOfDay = this[keys.minutes] ?: default.minuteOfDay
    )

    private fun MutablePreferences.setReminder(keys: ReminderKeys, reminder: MealReminder) {
        this[keys.enabled] = reminder.enabled
        this[keys.minutes] = reminder.minuteOfDay
    }

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs ->
            // Defaults must match AppSettings() — repeated here because DataStore has no native defaults.
            AppSettings(
                notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
                isDarkTheme          = prefs[Keys.IS_DARK_THEME]         ?: true,
                language             = prefs[Keys.LANGUAGE]
                    ?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                    ?: AppLanguage.ROMANIAN,
                breakfastReminder = prefs.reminder(breakfastKeys, defaults.breakfastReminder),
                lunchReminder     = prefs.reminder(lunchKeys, defaults.lunchReminder),
                snacksReminder    = prefs.reminder(snacksKeys, defaults.snacksReminder),
                dinnerReminder    = prefs.reminder(dinnerKeys, defaults.dinnerReminder)
            )
        }

    override suspend fun update(settings: AppSettings) {
        dataStore.edit { prefs ->
            prefs[Keys.NOTIFICATIONS_ENABLED] = settings.notificationsEnabled
            prefs[Keys.IS_DARK_THEME]         = settings.isDarkTheme
            prefs[Keys.LANGUAGE]              = settings.language.name
            prefs.setReminder(breakfastKeys, settings.breakfastReminder)
            prefs.setReminder(lunchKeys, settings.lunchReminder)
            prefs.setReminder(snacksKeys, settings.snacksReminder)
            prefs.setReminder(dinnerKeys, settings.dinnerReminder)
        }
    }
}
