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

    private val dayCompleteKey = intPreferencesKey("scheduled_day_complete_minutes")

    /** `null` = day-complete reminder not scheduled. */
    suspend fun readDayComplete(): Int? = dataStore.data.first()[dayCompleteKey]

    suspend fun writeDayComplete(minute: Int?) {
        dataStore.edit { prefs ->
            if (minute == null) prefs.remove(dayCompleteKey) else prefs[dayCompleteKey] = minute
        }
    }

    private val streakKey = intPreferencesKey("scheduled_streak_minutes")

    /** `null` = streak reminder not scheduled. */
    suspend fun readStreak(): Int? = dataStore.data.first()[streakKey]

    suspend fun writeStreak(minute: Int?) {
        dataStore.edit { prefs ->
            if (minute == null) prefs.remove(streakKey) else prefs[streakKey] = minute
        }
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
