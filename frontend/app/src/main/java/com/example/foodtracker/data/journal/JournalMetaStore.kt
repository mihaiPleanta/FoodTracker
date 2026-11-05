package com.example.foodtracker.data.journal

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.time.LocalDate

internal val Context.journalMetaDataStore: DataStore<Preferences> by preferencesDataStore(name = "journal_meta")

/**
 * Facts about the local journal that outlive a process: the account it belongs to and the
 * day the whole mirror window was last downloaded. Cleared together with the journal.
 */
interface JournalMetaStore {
    suspend fun windowFetchedOn(): LocalDate?
    suspend fun setWindowFetchedOn(date: LocalDate)
    suspend fun ownerUid(): String?
    suspend fun setOwnerUid(uid: String)
    suspend fun clear()
}

class DataStoreJournalMeta(private val dataStore: DataStore<Preferences>) : JournalMetaStore {
    private val windowKey = stringPreferencesKey("window_fetched_on")
    private val ownerKey = stringPreferencesKey("owner_uid")

    private suspend fun prefs(): Preferences? = try {
        dataStore.data.first()
    } catch (_: IOException) {
        null
    }

    override suspend fun windowFetchedOn(): LocalDate? =
        prefs()?.get(windowKey)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    override suspend fun setWindowFetchedOn(date: LocalDate) {
        dataStore.edit { it[windowKey] = date.toString() }
    }

    override suspend fun ownerUid(): String? = prefs()?.get(ownerKey)

    override suspend fun setOwnerUid(uid: String) {
        dataStore.edit { it[ownerKey] = uid }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}

/** Non-persistent store (tests, or when no context is at hand). */
class InMemoryJournalMeta : JournalMetaStore {
    @Volatile private var window: LocalDate? = null
    @Volatile private var owner: String? = null

    override suspend fun windowFetchedOn(): LocalDate? = window
    override suspend fun setWindowFetchedOn(date: LocalDate) { window = date }
    override suspend fun ownerUid(): String? = owner
    override suspend fun setOwnerUid(uid: String) { owner = uid }
    override suspend fun clear() { window = null; owner = null }
}
