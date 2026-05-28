package com.example.foodtracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.foodtracker.model.AppLanguage
import com.example.foodtracker.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

// Extension property la nivel de fișier — singleton DataStore per proces.
// MainActivity îl folosește pentru a obține un DataStore<Preferences> pe care
// îl pasează la DataStoreSettingsRepository.
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
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

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs ->
            AppSettings(
                notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
                isDarkTheme          = prefs[Keys.IS_DARK_THEME]         ?: true,
                language             = prefs[Keys.LANGUAGE]
                    ?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                    ?: AppLanguage.ROMANIAN
            )
        }

    override suspend fun update(settings: AppSettings) {
        dataStore.edit { prefs ->
            prefs[Keys.NOTIFICATIONS_ENABLED] = settings.notificationsEnabled
            prefs[Keys.IS_DARK_THEME]         = settings.isDarkTheme
            prefs[Keys.LANGUAGE]              = settings.language.name
        }
    }
}
