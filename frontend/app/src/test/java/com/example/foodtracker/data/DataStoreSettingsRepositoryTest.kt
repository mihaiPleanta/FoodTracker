package com.example.foodtracker.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.foodtracker.model.AppLanguage
import com.example.foodtracker.model.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataStoreSettingsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repo: DataStoreSettingsRepository

    @Before
    fun setUp() {
        // newFolder() creates an empty dir; DataStore will create the file lazily.
        // An empty newFile() would break DataStore (not a valid serialized Preferences).
        val folder: File = tempFolder.newFolder("datastore")
        dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(folder, "app_settings.preferences_pb") }
        )
        repo = DataStoreSettingsRepository(dataStore)
    }

    @Test
    fun settings_emptyStore_returnsDefaults() = runTest {
        val settings = repo.settings.first()
        assertEquals(AppSettings(), settings)
    }

    @Test
    fun update_thenRead_returnsSameValues() = runTest {
        val custom = AppSettings(
            notificationsEnabled = false,
            isDarkTheme = false,
            language = AppLanguage.ENGLISH
        )
        repo.update(custom)
        val read = repo.settings.first()
        assertEquals(custom, read)
    }

    @Test
    fun update_partialFieldsViaCopy_preservesOthers() = runTest {
        repo.update(AppSettings().copy(notificationsEnabled = false))
        val afterFirst = repo.settings.first()
        repo.update(afterFirst.copy(language = AppLanguage.ENGLISH))

        val final = repo.settings.first()
        assertEquals(false, final.notificationsEnabled)
        assertEquals(true, final.isDarkTheme)          // default preserved
        assertEquals(AppLanguage.ENGLISH, final.language)
    }

    @Test
    fun settings_invalidLanguageInStore_fallsBackToRomanian() = runTest {
        // Write an invalid AppLanguage value directly into the store
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("language")] = "KLINGON"
        }
        val read = repo.settings.first()
        assertEquals(AppLanguage.ROMANIAN, read.language)
    }

    @Test
    fun update_subsequentReadOnSameFlow_returnsNewValue() = runTest {
        // Read initial state (defaults)
        val initial = repo.settings.first()
        assertEquals(AppSettings(), initial)

        // Update
        repo.update(AppSettings(notificationsEnabled = false))

        // Next read reflects new value
        val afterUpdate = repo.settings.first()
        assertEquals(false, afterUpdate.notificationsEnabled)
    }
}
