package com.example.foodtracker.sync

import com.example.foodtracker.model.DayStatus
import com.example.foodtracker.model.FoodLogUpdateDto
import com.example.foodtracker.model.StatsGoalsDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorMergeTest {
    @Test fun serverRowsReplaceNonPendingLocal() {
        val r = mergeServerDay(listOf(log("a", 1, grams = 200, serverId = 5)), listOf(log("a", 1, grams = 100, serverId = 5)), emptyList())
        assertEquals(1, r.size)
        assertEquals(200, r[0].logged.grams)
        assertFalse(r[0].pending)
    }

    @Test fun localNonPendingAbsentOnServerIsDropped() {
        assertTrue(mergeServerDay(emptyList(), listOf(log("a", 1)), emptyList()).isEmpty())
    }

    @Test fun localPendingCreateAbsentOnServerIsKept() {
        val r = mergeServerDay(listOf(log("a", 1)), listOf(log("b", 2, pending = true)), listOf(SyncOp.CreateLog("b", createBody())))
        assertEquals(listOf("a", "b"), r.map { it.clientId })
        assertTrue(r[1].pending)
        assertTrue(r[1].logged.pending)
    }

    @Test fun localPendingWithoutQueuedOpAbsentOnServerIsDropped() {
        assertTrue(mergeServerDay(emptyList(), listOf(log("a", 1, pending = true)), emptyList()).isEmpty())
    }

    @Test fun touchedRowWithoutLocalCopyFallsBackToServerPending() {
        val r = mergeServerDay(listOf(log("a", 1)), emptyList(), listOf(SyncOp.UpdateLog("a", FoodLogUpdateDto(5))))
        assertTrue(r[0].pending)
        assertTrue(r[0].logged.pending)
    }

    @Test fun serverDayDoesNotResurrectPendingDelete() {
        val r = mergeServerDay(listOf(log("a", 1), log("b", 2)), emptyList(), listOf(SyncOp.DeleteLog("a")))
        assertEquals(listOf("b"), r.map { it.clientId })
    }

    @Test fun pendingUpdateKeepsLocalVersion() {
        val r = mergeServerDay(listOf(log("a", 1, grams = 100)), listOf(log("a", 1, grams = 300)), listOf(SyncOp.UpdateLog("a", FoodLogUpdateDto(300))))
        assertEquals(300, r[0].logged.grams)
        assertTrue(r[0].pending)
        assertTrue(r[0].logged.pending)
    }

    @Test fun resultSortedBySortKey() {
        val r = mergeServerDay(listOf(log("a", 5), log("c", 1)), listOf(log("b", 3, pending = true)), listOf(SyncOp.CreateLog("b", createBody())))
        assertEquals(listOf("c", "b", "a"), r.map { it.clientId })
    }

    private val g1 = StatsGoalsDto(2000, 100, 200, 60, 2200)
    private val g2 = StatsGoalsDto(1800, 90, 180, 50, 2000)

    @Test fun dayValuesServerWinsWithoutPending() {
        val r = mergeServerDayValues(LocalDayValues(2f, DayStatus.COMPLETE, g1), LocalDayValues(1f, null, g2), "d", emptyList())
        assertEquals(LocalDayValues(2f, DayStatus.COMPLETE, g1), r)
    }

    @Test fun pendingHydrationWinsOverServer() {
        val r = mergeServerDayValues(
            LocalDayValues(2f, DayStatus.COMPLETE, g1), LocalDayValues(1f, DayStatus.INCOMPLETE, g2), "d",
            listOf(SyncOp.PutHydration("d", 1f), SyncOp.PutDayStatus("d", false)),
        )
        assertEquals(LocalDayValues(1f, DayStatus.INCOMPLETE, g2), r)
    }

    @Test fun pendingForOtherDateIgnored() {
        val r = mergeServerDayValues(LocalDayValues(2f, null, g1), LocalDayValues(1f, null, g2), "d", listOf(SyncOp.PutHydration("x", 1f)))
        assertEquals(2f, r.hydrationL)
    }

    @Test fun nullLocalReturnsServer() {
        val s = LocalDayValues(2f, null, null)
        assertEquals(s, mergeServerDayValues(s, null, "d", emptyList()))
    }
}
