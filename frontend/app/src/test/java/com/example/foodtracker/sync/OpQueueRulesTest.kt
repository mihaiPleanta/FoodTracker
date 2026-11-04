package com.example.foodtracker.sync

import com.example.foodtracker.model.FoodLogIngredientDto
import com.example.foodtracker.model.FoodLogUpdateDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpQueueRulesTest {
    @Test fun updateFoldsIntoPendingCreate() {
        val create = SyncOp.CreateLog("c1", createBody())
        val r = enqueue(listOf(q(create, 1)), SyncOp.UpdateLog("c1", FoodLogUpdateDto(250, unit = "tbsp", unitQty = 2f)))
        assertEquals(1, r.size)
        val body = (r[0].op as SyncOp.CreateLog).body
        assertEquals(250, body.grams)
        assertEquals("tbsp", body.unit)
        assertEquals(2f, body.unitQty)
        assertEquals(1L, r[0].seq)
    }

    @Test fun updateWithIngredientsRecomputesPer100g() {
        val ing = listOf(
            FoodLogIngredientDto("a", 100f, 100f, 10f, 20f, 1f),
            FoodLogIngredientDto("b", 100f, 300f, 0f, 40f, 5f),
        )
        val r = enqueue(listOf(q(SyncOp.CreateLog("c1", createBody()), 1)), SyncOp.UpdateLog("c1", FoodLogUpdateDto(200, ing)))
        val body = (r[0].op as SyncOp.CreateLog).body
        assertEquals(200f, body.kcal100g, 0.01f)
        assertEquals(5f, body.protein100g, 0.01f)
        assertEquals(30f, body.carbs100g, 0.01f)
        assertEquals(3f, body.fat100g, 0.01f)
        assertEquals(ing, body.ingredients)
    }

    @Test fun updateOfInFlightCreateIsAppended() {
        val r = enqueue(listOf(q(SyncOp.CreateLog("c1", createBody()), 1, inFlight = true)), SyncOp.UpdateLog("c1", FoodLogUpdateDto(5)))
        assertEquals(2, r.size)
        assertTrue(r[1].op is SyncOp.UpdateLog)
    }

    @Test fun secondUpdateReplacesFirst() {
        val r = enqueue(listOf(q(SyncOp.UpdateLog("c1", FoodLogUpdateDto(5)), 3)), SyncOp.UpdateLog("c1", FoodLogUpdateDto(9)))
        assertEquals(1, r.size)
        assertEquals(9, (r[0].op as SyncOp.UpdateLog).body.grams)
    }

    @Test fun updateDoesNotReplaceInFlightUpdate() {
        val r = enqueue(listOf(q(SyncOp.UpdateLog("c1", FoodLogUpdateDto(5)), 3, inFlight = true)), SyncOp.UpdateLog("c1", FoodLogUpdateDto(9)))
        assertEquals(2, r.size)
    }

    @Test fun deleteOfPendingCreateCancelsEverything() {
        val queue = listOf(q(SyncOp.CreateLog("c1", createBody()), 1), q(SyncOp.UpdateLog("c1", FoodLogUpdateDto(5)), 2), q(SyncOp.PutHydration("d", 1f), 3))
        val r = enqueue(queue, SyncOp.DeleteLog("c1"))
        assertEquals(listOf(SyncOp.PutHydration("d", 1f)), r.map { it.op })
    }

    @Test fun deleteOfSyncedLogDropsPendingUpdates() {
        val r = enqueue(listOf(q(SyncOp.UpdateLog("c1", FoodLogUpdateDto(5)), 2)), SyncOp.DeleteLog("c1"))
        assertEquals(listOf<SyncOp>(SyncOp.DeleteLog("c1")), r.map { it.op })
    }

    @Test fun deleteAfterInFlightCreateIsAppended() {
        val r = enqueue(listOf(q(SyncOp.CreateLog("c1", createBody()), 1, inFlight = true)), SyncOp.DeleteLog("c1"))
        assertEquals(2, r.size)
        assertTrue(r[1].op is SyncOp.DeleteLog)
    }

    @Test fun hydrationKeepsOnlyLatestForDate() {
        var qu = enqueue(emptyList(), SyncOp.PutHydration("d1", 1f))
        qu = enqueue(qu, SyncOp.PutHydration("d2", 1f))
        qu = enqueue(qu, SyncOp.PutHydration("d1", 2f))
        assertEquals(listOf<SyncOp>(SyncOp.PutHydration("d2", 1f), SyncOp.PutHydration("d1", 2f)), qu.map { it.op })
    }

    @Test fun weightKeepsOnlyLatestForDate() {
        var qu = enqueue(emptyList(), SyncOp.PutWeight("d1", 70f))
        qu = enqueue(qu, SyncOp.PutWeight("d1", 71f))
        assertEquals(listOf<SyncOp>(SyncOp.PutWeight("d1", 71f)), qu.map { it.op })
    }

    @Test fun statusNullReplacesPendingComplete() {
        val qu = enqueue(listOf(q(SyncOp.PutDayStatus("d1", true), 1)), SyncOp.PutDayStatus("d1", null))
        assertEquals(listOf<SyncOp>(SyncOp.PutDayStatus("d1", null)), qu.map { it.op })
    }

    @Test fun inFlightPutIsKept() {
        val qu = enqueue(listOf(q(SyncOp.PutHydration("d1", 1f), 1, inFlight = true)), SyncOp.PutHydration("d1", 2f))
        assertEquals(2, qu.size)
    }

    @Test fun opDate() {
        assertEquals("2026-11-04", SyncOp.CreateLog("c1", createBody()).date)
        assertEquals("d", SyncOp.PutWeight("d", 1f).date)
        assertEquals(null, SyncOp.DeleteLog("c1").date)
    }

    @Test fun rejectedCreateDropsLaterOpsForSameClientId() {
        val rej = q(SyncOp.CreateLog("c1", createBody()), 1)
        val queue = listOf(rej, q(SyncOp.UpdateLog("c1", FoodLogUpdateDto(5)), 2), q(SyncOp.DeleteLog("c1"), 3), q(SyncOp.UpdateLog("c2", FoodLogUpdateDto(5)), 4))
        assertEquals(listOf(4L), dropDependents(queue, rej).map { it.seq })
    }

    @Test fun rejectedUpdateOnlyRemovesItself() {
        val rej = q(SyncOp.UpdateLog("c1", FoodLogUpdateDto(5)), 1)
        val queue = listOf(rej, q(SyncOp.DeleteLog("c1"), 2))
        assertEquals(listOf(2L), dropDependents(queue, rej).map { it.seq })
    }
}
