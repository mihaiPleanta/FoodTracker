package com.example.foodtracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalDate

// DataStore separat de app_settings: ritm de scriere diferit (la fiecare adăugare de
// mâncare), concern diferit (cache local pentru skip-if-logged). Co-locația ar mări
// fișierul de prefs și ar amesteca două ritmuri de scriere.
internal val Context.mealLogTrackerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "meal_log_tracker"
)

class MealLogTracker(
    private val dataStore: DataStore<Preferences>,
) {
    private val key = stringSetPreferencesKey("logged_meals")

    /** Marchează masa ca fiind logată în ziua dată. Curăță intrările mai vechi de 2 zile. */
    suspend fun markLogged(date: LocalDate, meal: String) {
        val newEntry = "$date:$meal"
        val cutoff = date.minusDays(2)
        dataStore.edit { prefs ->
            val existing = prefs[key].orEmpty()
            val kept = existing.filter { entry ->
                val entryDate = runCatching {
                    LocalDate.parse(entry.substringBefore(":"))
                }.getOrNull()
                entryDate != null && !entryDate.isBefore(cutoff)
            }.toSet()
            prefs[key] = kept + newEntry
        }
    }

    suspend fun wasLogged(date: LocalDate, meal: String): Boolean {
        val target = "$date:$meal"
        val set = dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { it[key].orEmpty() }
            .first()
        return target in set
    }
}
