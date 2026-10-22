package com.example.foodtracker.util.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.foodtracker.util.notifications.MealReminderScheduler.Meal
import kotlinx.coroutines.flow.first

// Separat de app_settings: ține ce s-a programat EFECTIV în WorkManager, nu ce vrea utilizatorul.
internal val Context.reminderScheduleDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "reminder_schedule"
)

class ScheduledReminderStore(
    private val dataStore: DataStore<Preferences>
) {
    private val initialized = booleanPreferencesKey("initialized")

    private fun minutesKey(meal: Meal) = intPreferencesKey("scheduled_${meal.name.lowercase()}_minutes")

    /** `null` when nothing was ever written (first run after the update). */
    suspend fun read(): ReminderSchedule? {
        val prefs = dataStore.data.first()
        if (prefs[initialized] != true) return null
        return Meal.values().associateWith { prefs[minutesKey(it)] }
    }

    suspend fun write(schedule: ReminderSchedule) {
        dataStore.edit { prefs ->
            prefs[initialized] = true
            Meal.values().forEach { meal ->
                val minutes = schedule[meal]
                if (minutes == null) prefs.remove(minutesKey(meal)) else prefs[minutesKey(meal)] = minutes
            }
        }
    }
}
