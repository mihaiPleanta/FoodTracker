package com.example.foodtracker.data.journal

import com.example.foodtracker.api.DaysApi
import com.example.foodtracker.api.LogsApi
import com.example.foodtracker.model.DayRangeItemDto
import com.example.foodtracker.model.DayResponseDto
import com.example.foodtracker.model.DayStatusResponseDto
import com.example.foodtracker.model.DayStatusUpdateDto
import com.example.foodtracker.model.DaysRangeResponseDto
import com.example.foodtracker.model.FoodIngredient
import com.example.foodtracker.model.FoodItem
import com.example.foodtracker.model.FoodLogCopyRequestDto
import com.example.foodtracker.model.FoodLogCreateDto
import com.example.foodtracker.model.FoodLogDeleteBatchDto
import com.example.foodtracker.model.FoodLogDeleteBatchResultDto
import com.example.foodtracker.model.FoodLogDto
import com.example.foodtracker.model.FoodLogSummaryDto
import com.example.foodtracker.model.FoodLogUpdateDto
import com.example.foodtracker.model.HydrationDto
import com.example.foodtracker.model.HydrationUpdateDto
import com.example.foodtracker.model.LoggedFood
import com.example.foodtracker.model.StatsDaysResponseDto
import com.example.foodtracker.model.WeightCheckInCreateDto
import com.example.foodtracker.model.WeightCheckInDto
import com.example.foodtracker.model.WeightCheckInListDto
import com.example.foodtracker.sync.QueuedOp
import com.example.foodtracker.sync.SyncOp
import com.example.foodtracker.sync.SyncOutcome
import com.example.foodtracker.sync.SyncScheduler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.time.LocalDate

internal class FakeSyncScheduler : SyncScheduler {
    var calls = 0
    override fun requestSync() { calls++ }
}

private class JLogsApi : LogsApi {
    val days = mutableMapOf<String, DayResponseDto>()
    var range: List<DayRangeItemDto> = emptyList()
    var weights: List<WeightCheckInDto> = emptyList()
    var dayFailure: Throwable? = null
    val dayRequests = mutableListOf<String>()
    val rangeRequests = mutableListOf<Pair<String, String>>()
    val weightRequests = mutableListOf<Pair<String, String>>()

    /** When set, the first getDay snapshots the answer, then suspends until completed (one-shot). */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun getDay(date: String): DayResponseDto {
        dayRequests += date
        dayFailure?.let { throw it }
        val snapshot = days[date] ?: DayResponseDto(emptyMap(), 0f, null)
        gate?.let { gate = null; it.await() }
        return snapshot
    }
    override suspend fun getDays(from: String, to: String): DaysRangeResponseDto {
        rangeRequests += from to to
        return DaysRangeResponseDto(range)
    }
    override suspend fun getWeightCheckIns(from: String, to: String): WeightCheckInListDto {
        weightRequests += from to to
        return WeightCheckInListDto(weights)
    }
    override suspend fun createFoodLog(body: FoodLogCreateDto): FoodLogDto = error("unused")
    override suspend fun updateFoodLog(id: Long, body: FoodLogUpdateDto): FoodLogDto = error("unused")
    override suspend fun updateFoodLogByClient(cid: String, body: FoodLogUpdateDto): FoodLogDto = error("unused")
    override suspend fun deleteFoodLogByClient(cid: String): Response<Unit> = error("unused")
    override suspend fun getFoodLogSummary(from: String, to: String): FoodLogSummaryDto = error("unused")
    override suspend fun copyFoodLogs(body: FoodLogCopyRequestDto): List<FoodLogDto> = error("unused")
    override suspend fun deleteFoodLogs(body: FoodLogDeleteBatchDto): FoodLogDeleteBatchResultDto = error("unused")
    override suspend fun deleteFoodLog(id: Long): Response<Unit> = error("unused")
    override suspend fun putHydration(date: String, body: HydrationUpdateDto): HydrationDto = error("unused")
    override suspend fun postWeightCheckIn(body: WeightCheckInCreateDto): WeightCheckInDto = error("unused")
}

private class JDaysApi : DaysApi {
    override suspend fun putStatus(date: String, body: DayStatusUpdateDto): DayStatusResponseDto = error("unused")
    override suspend fun deleteStatus(date: String): Response<Unit> = error("unused")
    override suspend fun getStatsDays(from: String, to: String): StatsDaysResponseDto = error("unused")
}

private val oats = FoodItem("123", "Oats", per100g = 380, protein100g = 13f, carbs100g = 60f, fat100g = 7f)

private fun serverLog(id: Long, cid: String, date: String, grams: Int = 100, createdAt: String? = null) = FoodLogDto(
    id = id, clientId = cid, createdAt = createdAt, logDate = date, meal = "DINNER", grams = grams,
    barcode = "b$id", name = "Food $id", kcal100g = 100f, protein100g = 1f, carbs100g = 1f, fat100g = 1f,
)

@OptIn(ExperimentalCoroutinesApi::class)
class JournalRepositoryTest {
    private val today = LocalDate.of(2026, 11, 4)
    private var clock = 1_000L
    private val dao = FakeUserDataDao()
    private val logsApi = JLogsApi()
    private val scheduler = FakeSyncScheduler()
    private val repo = JournalRepository(
        dao = dao,
        tx = object : TxRunner {
            override suspend fun <R> run(block: suspend () -> R): R = block()
        },
        logsApi = logsApi,
        daysApi = JDaysApi(),
        scheduler = scheduler,
        now = { clock++ },
        today = { today },
    )

    private suspend fun queue(): List<QueuedOp> = buildList {
        // Drain a snapshot through the public API without mutating it.
        dao.getOps().forEach { add(JournalJson.toQueued(it)) }
    }

    private fun TestScope.collectEvents(): MutableList<SyncEvent> {
        val events = mutableListOf<SyncEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repo.events.collect { events += it } }
        return events
    }

    @Test fun addLogShowsPendingInObserveDayAndRequestsSync() = runTest {
        val cid = repo.addLog(today, "Lunch", LoggedFood(oats, 50, unit = "lingură", unitQty = 2f))

        val day = repo.observeDay(today).first()
        val lunch = day.meals.getValue("Lunch")
        assertEquals(1, lunch.size)
        assertEquals(cid, lunch[0].clientId)
        assertTrue(lunch[0].pending)
        assertEquals(50, lunch[0].grams)
        assertEquals(1_000L, lunch[0].sortKey)
        assertEquals("lingură", lunch[0].unit)
        assertTrue(day.meals.getValue("Breakfast").isEmpty())
        assertEquals(1, scheduler.calls)
        assertEquals(1, repo.pendingCount.first())

        val op = queue().single().op as SyncOp.CreateLog
        assertEquals(cid, op.clientId)
        assertEquals(cid, op.body.clientId)
        assertEquals("2026-11-04", op.body.logDate)
        assertEquals("LUNCH", op.body.meal)
        assertEquals(380f, op.body.kcal100g)
        assertEquals("2026-11-04", dao.getOps().single().date)
        assertTrue(repo.hasLocalLog(today))
        assertFalse(repo.hasLocalLog(today.minusDays(1)))
    }

    @Test fun addLogReusesGivenSortKey() = runTest {
        repo.addLog(today, "Lunch", LoggedFood(oats, 50), sortKey = 7L)
        assertEquals(7L, repo.observeDay(today).first().meals.getValue("Lunch")[0].sortKey)
    }

    @Test fun updateAndDeleteWorkOnUnsyncedLog() = runTest {
        val cid = repo.addLog(today, "Lunch", LoggedFood(oats, 50))
        repo.updateLog(cid, LoggedFood(oats, 200, unit = "bol", unitQty = 1f))

        val lunch = repo.observeDay(today).first().meals.getValue("Lunch")
        assertEquals(200, lunch.single().grams)
        assertTrue(lunch.single().pending)
        val create = queue().single().op as SyncOp.CreateLog
        assertEquals(200, create.body.grams)
        assertEquals("bol", create.body.unit)

        repo.deleteLogs(listOf(cid))
        assertTrue(repo.observeDay(today).first().meals.values.all { it.isEmpty() })
        assertEquals(0, repo.pendingCount.first())
        assertEquals(3, scheduler.calls)
    }

    @Test fun copyLogsBuildsCreatesLikeServerCopy() = runTest {
        val recipe = LoggedFood(
            FoodItem(
                "r1", "Ciorbă", per100g = 50, protein100g = 2f, carbs100g = 5f, fat100g = 1f,
                ingredients = listOf(
                    FoodIngredient("A", 100f, 100f, 10f, 0f, 0f),
                    FoodIngredient("B", 50f, 200f, 0f, 20f, 4f),
                ),
            ),
            grams = 999, id = 5, clientId = "src-r", unit = "bol", unitQty = 1f,
        )
        val withUnit = LoggedFood(oats, 30, id = 6, clientId = "src-u", unit = "lingură", unitQty = 2f)
        val simple = LoggedFood(oats.copy(barcode = "999"), 120, clientId = "src-s")
        val target = today.plusDays(1)

        val ids = repo.copyLogs(target, listOf("Dinner" to recipe, "Breakfast" to withUnit, "Snacks" to simple))

        assertEquals(3, ids.toSet().size)
        assertTrue(ids.none { it.startsWith("src") })
        val bodies = queue().map { (it.op as SyncOp.CreateLog).body }
        assertEquals(ids, bodies.map { it.clientId })
        assertTrue(bodies.all { it.logDate == "2026-11-05" })
        assertEquals(listOf("DINNER", "BREAKFAST", "SNACKS"), bodies.map { it.meal })

        val r = bodies[0]
        assertEquals(150, r.grams)
        assertEquals(200f / 150f * 100f, r.kcal100g, 0.01f)
        assertEquals(10f / 150f * 100f, r.protein100g, 0.01f)
        assertEquals(10f / 150f * 100f, r.carbs100g, 0.01f)
        assertEquals(2f / 150f * 100f, r.fat100g, 0.01f)
        assertEquals(2, r.ingredients!!.size)
        assertNull(r.unit)
        assertNull(r.unitQty)

        val u = bodies[1]
        assertEquals(30, u.grams)
        assertEquals("lingură", u.unit)
        assertEquals(2f, u.unitQty)
        assertEquals(380f, u.kcal100g)
        assertNull(u.ingredients)

        val s = bodies[2]
        assertEquals(120, s.grams)
        assertNull(s.unit)
        assertNull(s.ingredients)

        val day = repo.observeDay(target).first()
        assertEquals(150, day.meals.getValue("Dinner").single().grams)
        assertNull(day.meals.getValue("Dinner").single().unit)
        assertTrue(day.meals.values.flatten().all { it.pending })
        assertEquals(1, scheduler.calls)
    }

    @Test fun refreshDayKeepsPendingLocalChanges() = runTest {
        val cid = repo.addLog(today, "Lunch", LoggedFood(oats, 50))
        repo.setHydration(today, 1.5f)
        logsApi.days["2026-11-04"] = DayResponseDto(
            foodsByMeal = mapOf("dinner" to listOf(serverLog(9, "srv-9", "2026-11-04", createdAt = "2026-11-04T08:00:00Z"))),
            hydrationLiters = 0.5f,
            weightCheckIn = WeightCheckInDto("2026-11-04", 80f),
            status = "complete",
        )

        repo.refreshDay(today)

        val day = repo.observeDay(today).first()
        assertEquals(cid, day.meals.getValue("Lunch").single().clientId)
        assertTrue(day.meals.getValue("Lunch").single().pending)
        val dinner = day.meals.getValue("Dinner").single()
        assertEquals(9L, dinner.id)
        assertFalse(dinner.pending)
        assertEquals(1.5f, day.hydrationL)
        assertEquals(80f, day.weightKg)
        assertEquals(com.example.foodtracker.model.DayStatus.COMPLETE, day.status)
        assertTrue(day.fetched)
        assertEquals(java.time.Instant.parse("2026-11-04T08:00:00Z").toEpochMilli(), dinner.sortKey)
    }

    @Test fun refreshWindowMarksDaysFetchedAndStoresWeights() = runTest {
        val stale = today.minusDays(5).toString()
        dao.upsertDays(listOf(DayEntity(stale, 3f, "complete", null, null)))
        dao.upsertLogs(listOf(LogEntity("old", 1, stale, "LUNCH", 1, JournalJson.encodeLogged(LoggedFood(oats, 10)), false)))
        val pendingDay = today.minusDays(3)
        repo.setHydration(pendingDay, 2.2f)
        val yesterday = today.minusDays(1).toString()
        logsApi.range = listOf(
            DayRangeItemDto(
                date = yesterday,
                foodsByMeal = mapOf("dinner" to listOf(serverLog(3, "srv-3", yesterday))),
                hydrationLiters = 2f,
                weightCheckIn = null,
                status = "complete",
                goals = com.example.foodtracker.model.StatsGoalsDto(2000, 100, 200, 60, 2300),
            ),
        )
        logsApi.weights = listOf(WeightCheckInDto(today.minusDays(100).toString(), 82f))

        repo.refreshWindow()

        assertEquals(listOf("2026-09-04" to "2026-11-04"), logsApi.rangeRequests)
        assertEquals(listOf("2026-07-06" to "2026-11-04"), logsApi.weightRequests)
        val y = repo.observeDay(today.minusDays(1)).first()
        assertEquals(1, y.meals.getValue("Dinner").size)
        assertEquals(2f, y.hydrationL)
        assertTrue(y.fetched)
        val s = repo.observeDay(today.minusDays(5)).first()
        assertTrue(s.meals.values.all { it.isEmpty() })
        assertEquals(0f, s.hydrationL)
        assertNull(s.status)
        assertTrue(s.fetched)
        assertEquals(2.2f, repo.observeDay(pendingDay).first().hydrationL)
        assertTrue(repo.observeDay(today.minusDays(61)).first().fetched)
        assertEquals(listOf(today.minusDays(100) to 82f), repo.observeWeights().first())
    }

    @Test fun isCoveredFalseBeforeWindowFetchAndOutsideWindow() = runTest {
        assertFalse(repo.isCovered(today.minusDays(3), today))
        repo.refreshWindow()
        assertTrue(repo.isCovered(today.minusDays(3), today))
        assertTrue(repo.isCovered(today.minusDays(61), today))
        assertFalse(repo.isCovered(today.minusDays(62), today))
        assertFalse(repo.isCovered(today, today.plusDays(1)))
    }

    @Test fun completeOpClearsPendingAndEmitsSynced() = runTest {
        val events = collectEvents()
        val cid = repo.addLog(today, "Lunch", LoggedFood(oats, 50))
        val op = repo.nextOp()!!
        repo.markInFlight(op.seq)

        repo.completeOp(op, serverLog(42, cid, "2026-11-04", grams = 50).copy(meal = "LUNCH"))

        val item = repo.observeDay(today).first().meals.getValue("Lunch").single()
        assertFalse(item.pending)
        assertEquals(42L, item.id)
        assertEquals(1_000L, item.sortKey)
        assertEquals(0, repo.pendingCount.first())
        assertNull(repo.nextOp())
        assertEquals(listOf<SyncEvent>(SyncEvent.Synced(setOf("2026-11-04"))), events)
    }

    @Test fun completeOpKeepsPendingWhileAnotherOpRemains() = runTest {
        val cid = repo.addLog(today, "Lunch", LoggedFood(oats, 50))
        val op = repo.nextOp()!!
        repo.markInFlight(op.seq)
        repo.updateLog(cid, LoggedFood(oats, 70))

        repo.completeOp(op, serverLog(42, cid, "2026-11-04", grams = 50).copy(meal = "LUNCH"))

        val item = repo.observeDay(today).first().meals.getValue("Lunch").single()
        assertTrue(item.pending)
        assertEquals(42L, item.id)
        assertEquals(70, item.grams)
        assertTrue(repo.nextOp()!!.op is SyncOp.UpdateLog)
    }

    @Test fun rejectNotifyEmitsRejectedAndDropsDependents() = runTest {
        val events = collectEvents()
        val cid = repo.addLog(today, "Lunch", LoggedFood(oats, 50))
        val create = repo.nextOp()!!
        repo.markInFlight(create.seq)
        repo.updateLog(cid, LoggedFood(oats, 70))
        repo.setHydration(today, 1f)
        assertEquals(3, queue().size)

        repo.rejectOp(create, SyncOutcome.DROP_NOTIFY)

        assertTrue(queue().single().op is SyncOp.PutHydration)
        assertEquals(listOf("2026-11-04"), logsApi.dayRequests)
        assertTrue(repo.observeDay(today).first().meals.values.all { it.isEmpty() })
        assertEquals(1f, repo.observeDay(today).first().hydrationL)
        assertEquals(listOf<SyncEvent>(SyncEvent.Rejected), events)
    }

    @Test fun rejectSilentIgnoresRefreshFailureAndEmitsNothing() = runTest {
        val events = collectEvents()
        val cid = repo.addLog(today, "Lunch", LoggedFood(oats, 50))
        val create = repo.nextOp()!!
        repo.markInFlight(create.seq)
        repo.deleteLogs(listOf(cid))
        val delete = queue().last()
        logsApi.dayFailure = IOException("offline")

        repo.rejectOp(delete, SyncOutcome.DROP_SILENT)

        assertEquals(listOf("2026-11-04"), logsApi.dayRequests)
        assertEquals(listOf(create.seq), queue().map { it.seq })
        assertTrue(events.isEmpty())
    }

    @Test fun clearAllEmptiesEverything() = runTest {
        repo.addLog(today, "Lunch", LoggedFood(oats, 50))
        repo.setHydration(today, 1f)
        repo.setWeight(today, 80f)
        repo.setDayStatus(today, true)
        dao.upsertQuickPick(QuickPickEntity("recent", "[]"))

        repo.clearAll()

        assertTrue(dao.logs.value.isEmpty())
        assertTrue(dao.days.value.isEmpty())
        assertTrue(dao.weights.value.isEmpty())
        assertTrue(dao.ops.value.isEmpty())
        assertTrue(dao.quickPicks.value.isEmpty())
        assertEquals(0, repo.pendingCount.first())
    }

    @Test fun localValueWritesAreVisibleAndQueued() = runTest {
        repo.setHydration(today, 1f)
        repo.setHydration(today, 2f)
        repo.setWeight(today, 80f)
        repo.setDayStatus(today, true)
        val day = repo.observeDay(today).first()
        assertEquals(2f, day.hydrationL)
        assertEquals(80f, day.weightKg)
        assertEquals(com.example.foodtracker.model.DayStatus.COMPLETE, day.status)
        assertEquals(
            listOf(SyncOp.PutHydration("2026-11-04", 2f), SyncOp.PutWeight("2026-11-04", 80f), SyncOp.PutDayStatus("2026-11-04", true)),
            queue().map { it.op },
        )
        repo.setDayStatus(today, null)
        assertNull(repo.observeDay(today).first().status)
    }

    @Test fun createRetriedAfterTimeoutIsSentWithSameClientId() = runTest {
        val cid = repo.addLog(today, "Lunch", LoggedFood(oats, 50))
        val op = repo.nextOp()!!
        repo.markInFlight(op.seq)
        assertTrue(repo.nextOp()!!.inFlight)

        repo.releaseInFlight()

        val again = repo.nextOp()!!
        assertEquals(op.seq, again.seq)
        assertFalse(again.inFlight)
        assertTrue(again.attempted)
        assertEquals(cid, (again.op as SyncOp.CreateLog).body.clientId)
        assertNotEquals(0L, again.seq)
    }

    @Test fun refreshSkipsStaleSnapshotAfterCompleteOp() = runTest {
        val cid = repo.addLog(today, "Lunch", LoggedFood(oats, 50))
        val create = repo.nextOp()!!
        repo.markInFlight(create.seq)
        val gate = CompletableDeferred<Unit>()
        logsApi.gate = gate
        val refresh = launch { repo.refreshDay(today) }   // snapshot taken now: the day is still empty
        runCurrent()

        val synced = serverLog(42, cid, "2026-11-04", grams = 50).copy(meal = "LUNCH")
        repo.completeOp(create, synced)
        logsApi.days["2026-11-04"] = DayResponseDto(mapOf("lunch" to listOf(synced)), 0f, null)
        gate.complete(Unit)
        refresh.join()

        val item = repo.observeDay(today).first().meals.getValue("Lunch").single()
        assertEquals(cid, item.clientId)
        assertEquals(42L, item.id)
        assertFalse(item.pending)
        assertEquals(2, logsApi.dayRequests.size)
    }

    @Test fun refreshAfterClearAllDoesNotWrite() = runTest {
        logsApi.days["2026-11-04"] = DayResponseDto(
            mapOf("dinner" to listOf(serverLog(9, "srv-9", "2026-11-04"))), 1f, WeightCheckInDto("2026-11-04", 80f), "complete",
        )
        val gate = CompletableDeferred<Unit>()
        logsApi.gate = gate
        val refresh = launch { repo.refreshDay(today) }
        runCurrent()

        repo.clearAll()
        gate.complete(Unit)
        refresh.join()

        assertTrue(dao.logs.value.isEmpty())
        assertTrue(dao.days.value.isEmpty())
        assertTrue(dao.weights.value.isEmpty())
        assertEquals(1, logsApi.dayRequests.size)
    }

    @Test fun rejectedCreateRemovesMirrorRowEvenOffline() = runTest {
        repo.addLog(today, "Lunch", LoggedFood(oats, 50))
        val create = repo.nextOp()!!
        repo.markInFlight(create.seq)
        logsApi.dayFailure = IOException("offline")

        repo.rejectOp(create, SyncOutcome.DROP_NOTIFY)

        assertTrue(dao.logs.value.isEmpty())
        assertFalse(repo.hasLocalLog(today))
        assertEquals(0, repo.pendingCount.first())
    }

    @Test fun refreshWindowKeepsLocalLogWithQueuedOp() = runTest {
        val cid = repo.addLog(today.minusDays(2), "Dinner", LoggedFood(oats, 50))

        repo.refreshWindow()

        val day = repo.observeDay(today.minusDays(2)).first()
        val item = day.meals.getValue("Dinner").single()
        assertEquals(cid, item.clientId)
        assertTrue(item.pending)
        assertTrue(day.fetched)
        assertEquals(1, repo.pendingCount.first())
    }
}
