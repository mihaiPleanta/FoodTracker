package com.example.foodtracker.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.foodtracker.model.AppLanguage
import com.example.foodtracker.model.AppSettings
import com.example.foodtracker.model.MealReminder
import androidx.datastore.preferences.core.booleanPreferencesKey
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

    @Test
    fun update_mealReminders_roundTrip() = runTest {
        val custom = AppSettings(
            lunchReminder = MealReminder(enabled = false, minuteOfDay = 12 * 60 + 30),
            dinnerReminder = MealReminder(enabled = true, minuteOfDay = 20 * 60 + 15)
        )
        repo.update(custom)
        assertEquals(custom, repo.settings.first())
    }

    @Test
    fun settings_legacyStoreWithoutReminderKeys_returnsReminderDefaults() = runTest {
        dataStore.edit { prefs ->
            prefs[booleanPreferencesKey("notifications_enabled")] = false
        }
        val read = repo.settings.first()
        assertEquals(false, read.notificationsEnabled)
        assertEquals(MealReminder(enabled = true, minuteOfDay = 480), read.breakfastReminder)
        assertEquals(MealReminder(enabled = true, minuteOfDay = 1140), read.dinnerReminder)
    }

    @Test
    fun dayCompleteReminder_defaultsToDisabled2130() = runTest {
        val r = repo.settings.first().dayCompleteReminder
        assertEquals(MealReminder(enabled = false, minuteOfDay = 21 * 60 + 30), r)
    }

    @Test
    fun dayCompleteReminder_roundTrips() = runTest {
        val custom = AppSettings(dayCompleteReminder = MealReminder(enabled = true, minuteOfDay = 22 * 60))
        repo.update(custom)
        assertEquals(custom.dayCompleteReminder, repo.settings.first().dayCompleteReminder)
    }

    @Test
    fun streakReminder_defaultsToEnabled2000() = runTest {
        assertEquals(MealReminder(enabled = true, minuteOfDay = 20 * 60), repo.settings.first().streakReminder)
    }

    @Test
    fun streakReminder_roundTrips() = runTest {
        val custom = AppSettings(streakReminder = MealReminder(enabled = false, minuteOfDay = 18 * 60))
        repo.update(custom)
        assertEquals(custom.streakReminder, repo.settings.first().streakReminder)
    }

    @Test
    fun tolerance_defaultsTo10() = runTest {
        assertEquals(10, repo.settings.first().inTargetTolerancePct)
    }

    @Test
    fun tolerance_roundTrips15() = runTest {
        repo.update(AppSettings(inTargetTolerancePct = 15))
        assertEquals(15, repo.settings.first().inTargetTolerancePct)
    }

    @Test
    fun tolerance_invalidStoredValue_readsAs10() = runTest {
        dataStore.edit { prefs ->
            prefs[androidx.datastore.preferences.core.intPreferencesKey("in_target_tolerance_pct")] = 7
        }
        assertEquals(10, repo.settings.first().inTargetTolerancePct)
    }
}
