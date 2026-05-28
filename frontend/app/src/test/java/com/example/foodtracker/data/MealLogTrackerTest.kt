package com.example.foodtracker.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

class MealLogTrackerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var tracker: MealLogTracker

    @Before
    fun setUp() {
        val folder: File = tempFolder.newFolder("datastore")
        dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(folder, "meal_log_tracker.preferences_pb") }
        )
        tracker = MealLogTracker(dataStore)
    }

    @Test
    fun wasLogged_emptyStore_returnsFalse() = runTest {
        assertFalse(tracker.wasLogged(LocalDate.of(2026, 5, 28), "Lunch"))
    }

    @Test
    fun markLogged_thenWasLogged_returnsTrueForSameKey() = runTest {
        tracker.markLogged(LocalDate.of(2026, 5, 28), "Lunch")
        assertTrue(tracker.wasLogged(LocalDate.of(2026, 5, 28), "Lunch"))
    }

    @Test
    fun markLogged_doesNotAffectOtherMealsSameDay() = runTest {
        tracker.markLogged(LocalDate.of(2026, 5, 28), "Lunch")
        assertFalse(tracker.wasLogged(LocalDate.of(2026, 5, 28), "Dinner"))
    }

    @Test
    fun markLogged_doesNotAffectSameMealDifferentDay() = runTest {
        tracker.markLogged(LocalDate.of(2026, 5, 28), "Lunch")
        assertFalse(tracker.wasLogged(LocalDate.of(2026, 5, 29), "Lunch"))
    }

    @Test
    fun markLogged_evictsEntriesOlderThanTwoDays() = runTest {
        // Seed via the public API to avoid coupling to the internal key name.
        tracker.markLogged(LocalDate.of(2026, 5, 25), "Lunch")
        tracker.markLogged(LocalDate.of(2026, 5, 28), "Breakfast")

        assertFalse(tracker.wasLogged(LocalDate.of(2026, 5, 25), "Lunch"))
        assertTrue(tracker.wasLogged(LocalDate.of(2026, 5, 28), "Breakfast"))
    }

    @Test
    fun markLogged_keepsEntryExactlyTwoDaysOld() = runTest {
        tracker.markLogged(LocalDate.of(2026, 5, 26), "Lunch")
        tracker.markLogged(LocalDate.of(2026, 5, 28), "Breakfast")
        assertTrue(tracker.wasLogged(LocalDate.of(2026, 5, 26), "Lunch"))
    }

    @Test
    fun markLogged_malformedEntriesAreDroppedSilently() = runTest {
        dataStore.edit { prefs ->
            prefs[stringSetPreferencesKey("logged_meals")] = setOf("garbage", "no-colon-here")
        }
        tracker.markLogged(LocalDate.of(2026, 5, 28), "Lunch")
        assertTrue(tracker.wasLogged(LocalDate.of(2026, 5, 28), "Lunch"))
    }
}
