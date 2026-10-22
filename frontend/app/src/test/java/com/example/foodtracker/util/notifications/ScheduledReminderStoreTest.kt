package com.example.foodtracker.util.notifications

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.foodtracker.util.notifications.MealReminderScheduler.Meal
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ScheduledReminderStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var store: ScheduledReminderStore

    @Before
    fun setUp() {
        val folder: File = tempFolder.newFolder("datastore")
        store = ScheduledReminderStore(
            PreferenceDataStoreFactory.create(
                produceFile = { File(folder, "reminder_schedule.preferences_pb") }
            )
        )
    }

    @Test
    fun read_neverWritten_returnsNull() = runTest {
        assertNull(store.read())
    }

    @Test
    fun write_thenRead_roundTripsIncludingNulls() = runTest {
        val schedule = mapOf(
            Meal.BREAKFAST to 480, Meal.LUNCH to null, Meal.SNACKS to 965, Meal.DINNER to null,
        )
        store.write(schedule)
        assertEquals(schedule, store.read())
    }

    @Test
    fun write_overwritesPreviousValueWithNull() = runTest {
        store.write(Meal.values().associateWith { 780 })
        store.write(Meal.values().associateWith { 780 } + (Meal.LUNCH to null))
        assertNull(store.read()!![Meal.LUNCH])
    }
}
