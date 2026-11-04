package com.example.foodtracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.foodtracker.model.GoalsDto
import com.example.foodtracker.model.ProfileDto
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import java.io.IOException

internal val Context.profileSnapshotDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "profile_snapshot"
)

/** Last server-confirmed profile and goals, used when the app starts offline. */
data class ProfileSnapshot(val profile: ProfileDto?, val goals: GoalsDto?)

class ProfileSnapshotStore(private val dataStore: DataStore<Preferences>) {
    private val gson = Gson()
    private val uidKey = stringPreferencesKey("uid")
    private val profileKey = stringPreferencesKey("profile")
    private val goalsKey = stringPreferencesKey("goals")

    /** Non-null arguments overwrite; null keeps the stored value of the same uid. A different uid replaces everything. */
    suspend fun save(uid: String, profile: ProfileDto?, goals: GoalsDto?) {
        dataStore.edit { prefs ->
            if (prefs[uidKey] != uid) prefs.clear()
            prefs[uidKey] = uid
            if (profile != null) prefs[profileKey] = gson.toJson(profile)
            if (goals != null) prefs[goalsKey] = gson.toJson(goals)
        }
    }

    /** The snapshot of [uid]; null when empty or taken for another account. */
    suspend fun read(uid: String): ProfileSnapshot? {
        val prefs = try {
            dataStore.data.first()
        } catch (_: IOException) {
            return null
        }
        if (prefs[uidKey] != uid) return null
        val profile = prefs[profileKey]?.let { runCatching { gson.fromJson(it, ProfileDto::class.java) }.getOrNull() }
        val goals = prefs[goalsKey]?.let { runCatching { gson.fromJson(it, GoalsDto::class.java) }.getOrNull() }
        return ProfileSnapshot(profile, goals)
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}
