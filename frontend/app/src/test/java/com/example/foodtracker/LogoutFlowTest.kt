package com.example.foodtracker

import com.example.foodtracker.data.LOGOUT_SYNC_TIMEOUT_MS
import com.example.foodtracker.data.LocalDataWiper
import com.example.foodtracker.data.LogoutCheck
import com.example.foodtracker.data.LogoutCoordinator
import com.example.foodtracker.model.FoodItem
import com.example.foodtracker.model.LoggedFood
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class LogoutFlowTest {

    @Test
    fun tryLogoutReadyWhenSyncDrains() = runTest {
        var timeout = -1L
        val c = LogoutCoordinator(
            sync = { timeout = it; true },
            pendingCount = { 0 },
            clearLocal = {},
        )
        assertEquals(LogoutCheck.Ready, c.tryLogout())
        assertEquals(LOGOUT_SYNC_TIMEOUT_MS, timeout)
        assertEquals(5000L, timeout)
    }

    @Test
    fun tryLogoutUnsyncedWhenOffline() = runTest {
        val c = LogoutCoordinator(
            sync = { false },
            pendingCount = { 3 },
            clearLocal = {},
        )
        assertEquals(LogoutCheck.Unsynced(3), c.tryLogout())
    }

    @Test
    fun tryLogoutAsksWhenTheCheckFails() = runTest {
        val syncFails = LogoutCoordinator(sync = { throw IllegalStateException("db") }, pendingCount = { 2 }, clearLocal = {})
        assertEquals(LogoutCheck.Unsynced(2), syncFails.tryLogout())
        val countFails = LogoutCoordinator(sync = { true }, pendingCount = { throw IllegalStateException("db") }, clearLocal = {})
        assertEquals(LogoutCheck.Unsynced(1), countFails.tryLogout())
    }

    @Test
    fun logoutClearsMirrorQueueAndQuickPicks() = runTest {
        val j = TestJournal()
        val date = LocalDate.of(2026, 11, 4)
        j.repo.setHydration(date, 1.5f)
        j.repo.addLog(
            date, "Breakfast",
            LoggedFood(FoodItem(barcode = "1", name = "x", per100g = 100, protein100g = 1f, carbs100g = 1f, fat100g = 1f), 100),
        )
        j.repo.saveQuickPicks("favorites", "[]")
        val c = LogoutCoordinator(
            sync = { false },
            pendingCount = { j.repo.pendingCount.first() },
            clearLocal = { j.repo.clearAll() },
        )
        assertEquals(LogoutCheck.Unsynced(2), c.tryLogout())

        c.clearLocal()

        assertEquals(0, j.repo.pendingCount.first())
        assertNull(j.repo.readQuickPicks("favorites"))
        val day = j.repo.observeDay(date).first()
        assertEquals(0f, day.hydrationL)
        assertEquals(true, day.meals.values.all { it.isEmpty() })
    }

    @Test
    fun wipeRunsAllStepsEvenIfOneFails() = runTest {
        val ran = mutableListOf<String>()
        LocalDataWiper(
            listOf(
                { ran += "food" },
                { ran += "journal"; throw IllegalStateException("disk") },
                { ran += "snapshot" },
            )
        ).wipe()
        assertEquals(listOf("food", "journal", "snapshot"), ran)
    }
}
