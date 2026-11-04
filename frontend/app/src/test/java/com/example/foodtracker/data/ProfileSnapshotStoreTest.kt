package com.example.foodtracker.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.foodtracker.model.GoalsDto
import com.example.foodtracker.model.ProfileDto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProfileSnapshotStoreTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun store() = ProfileSnapshotStore(
        PreferenceDataStoreFactory.create(
            produceFile = { File(tempFolder.newFolder(), "profile_snapshot.preferences_pb") }
        )
    )

    private val profile = ProfileDto("Ana", 30, "FEMALE", 165, 60f, 58f, "MODERATE", true, 2.5f)
    private val goals = GoalsDto(2000, 150, 250, 65, "MAINTENANCE", null, 2300)

    @Test
    fun roundTrip() = runTest {
        val s = store()
        s.save("u1", profile, goals)
        assertEquals(ProfileSnapshot(profile, goals), s.read("u1"))
    }

    @Test
    fun otherUidReadsNull() = runTest {
        val s = store()
        s.save("u1", profile, goals)
        assertNull(s.read("u2"))
    }

    @Test
    fun nullArgumentsKeepStoredValues() = runTest {
        val s = store()
        s.save("u1", profile, null)
        s.save("u1", null, goals)
        assertEquals(ProfileSnapshot(profile, goals), s.read("u1"))
    }

    @Test
    fun newUidReplacesOldData() = runTest {
        val s = store()
        s.save("u1", profile, goals)
        s.save("u2", null, goals)
        assertEquals(ProfileSnapshot(null, goals), s.read("u2"))
    }

    @Test
    fun clearEmpties() = runTest {
        val s = store()
        s.save("u1", profile, goals)
        s.clear()
        assertNull(s.read("u1"))
    }
}
