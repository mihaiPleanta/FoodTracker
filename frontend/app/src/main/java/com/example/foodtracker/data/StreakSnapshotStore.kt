package com.example.foodtracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.time.LocalDate

internal val Context.streakSnapshotDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "streak_snapshot"
)

/** Last server-confirmed streak, kept for offline display and the reminder. */
data class StreakSnapshot(
    val current: Int,
    val snapshotDate: LocalDate,
    val lastLoggedDate: LocalDate?,
)

interface StreakSnapshotStorage {
    suspend fun read(): StreakSnapshot?
    suspend fun write(s: StreakSnapshot)
    suspend fun clear()
}

class StreakSnapshotStore(
    private val dataStore: DataStore<Preferences>,
) : StreakSnapshotStorage {
    private val currentKey = intPreferencesKey("current")
    private val snapshotDateKey = stringPreferencesKey("snapshot_date")
    private val lastLoggedKey = stringPreferencesKey("last_logged_date")

    override suspend fun read(): StreakSnapshot? {
        val prefs = dataStore.data.first()
        val current = prefs[currentKey] ?: return null
        val date = prefs[snapshotDateKey]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return null
        val last = prefs[lastLoggedKey]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return StreakSnapshot(current, date, last)
    }

    override suspend fun write(s: StreakSnapshot) {
        dataStore.edit { prefs ->
            prefs[currentKey] = s.current
            prefs[snapshotDateKey] = s.snapshotDate.toString()
            if (s.lastLoggedDate == null) prefs.remove(lastLoggedKey)
            else prefs[lastLoggedKey] = s.lastLoggedDate.toString()
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}
