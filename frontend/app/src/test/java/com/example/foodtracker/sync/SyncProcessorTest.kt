package com.example.foodtracker.sync

import com.example.foodtracker.api.DaysApi
import com.example.foodtracker.api.LogsApi
import com.example.foodtracker.data.journal.FakeUserDataDao
import com.example.foodtracker.data.journal.JournalRepository
import com.example.foodtracker.data.journal.TxRunner
import com.example.foodtracker.model.DayRangeItemDto
import com.example.foodtracker.model.DayResponseDto
import com.example.foodtracker.model.DayStatusResponseDto
import com.example.foodtracker.model.DayStatusUpdateDto
import com.example.foodtracker.model.DaysRangeResponseDto
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.LocalDate

private fun httpError(code: Int) =
    HttpException(Response.error<Any>(code, "{}".toResponseBody("application/json".toMediaType())))

private class SLogsApi : LogsApi {
    val calls = mutableListOf<String>()
    /** Per-call failure for createFoodLog, keyed by client id. */
    val createFailures = mutableMapOf<String, Throwable>()
    var deleteCode = 204
    var createDelayMs = 0L
    var hang = false

    override suspend fun createFoodLog(body: FoodLogCreateDto): FoodLogDto {
        calls += "create:${body.clientId}"
        if (hang) awaitCancellation()
        if (createDelayMs > 0) delay(createDelayMs)
        createFailures[body.clientId]?.let { throw it }
        return FoodLogDto(
            id = 1, clientId = body.clientId.orEmpty(), logDate = body.logDate, meal = body.meal, grams = body.grams,
            barcode = body.barcode, name = body.name, kcal100g = 1f, protein100g = 1f, carbs100g = 1f, fat100g = 1f,
        )
    }
    override suspend fun updateFoodLogByClient(cid: String, body: FoodLogUpdateDto): FoodLogDto = error("unused")
    override suspend fun deleteFoodLogByClient(cid: String): Response<Unit> {
        calls += "delete:$cid"
        return if (deleteCode in 200..299) Response.success(deleteCode, Unit)
        else Response.error(deleteCode, "{}".toResponseBody("application/json".toMediaType()))
    }
    override suspend fun putHydration(date: String, body: HydrationUpdateDto): HydrationDto {
        calls += "hydration:$date"
        return HydrationDto(date, body.liters)
    }
    override suspend fun postWeightCheckIn(body: WeightCheckInCreateDto): WeightCheckInDto = error("unused")
    override suspend fun getDay(date: String): DayResponseDto = DayResponseDto(emptyMap(), 0f, null)
    override suspend fun getDays(from: String, to: String): DaysRangeResponseDto = DaysRangeResponseDto(emptyList<DayRangeItemDto>())
    override suspend fun getWeightCheckIns(from: String, to: String): WeightCheckInListDto = WeightCheckInListDto(emptyList())
    override suspend fun updateFoodLog(id: Long, body: FoodLogUpdateDto): FoodLogDto = error("unused")
    override suspend fun getFoodLogSummary(from: String, to: String): FoodLogSummaryDto = error("unused")
    override suspend fun copyFoodLogs(body: FoodLogCopyRequestDto): List<FoodLogDto> = error("unused")
    override suspend fun deleteFoodLogs(body: FoodLogDeleteBatchDto): FoodLogDeleteBatchResultDto = error("unused")
    override suspend fun deleteFoodLog(id: Long): Response<Unit> = error("unused")
}

private class SDaysApi : DaysApi {
    val calls = mutableListOf<String>()
    var deleteCode = 204
    override suspend fun putStatus(date: String, body: DayStatusUpdateDto): DayStatusResponseDto {
        calls += "put:$date:${body.complete}"
        return DayStatusResponseDto(date, if (body.complete) "complete" else "incomplete")
    }
    override suspend fun deleteStatus(date: String): Response<Unit> {
        calls += "delete:$date"
        return if (deleteCode in 200..299) Response.success(deleteCode, Unit)
        else Response.error(deleteCode, "{}".toResponseBody("application/json".toMediaType()))
    }
    override suspend fun getStatsDays(from: String, to: String): StatsDaysResponseDto = error("unused")
}

private class NoopScheduler : SyncScheduler { override fun requestSync() = Unit }

/** Serializes transactions like Room's exclusive write transaction. */
private class MutexTx : TxRunner {
    private val mutex = kotlinx.coroutines.sync.Mutex()
    override suspend fun <R> run(block: suspend () -> R): R = mutex.withLock { block() }
}

/** [onFirstOp] runs right after the queue head is read; [failDeleteOps] makes completing an op throw. */
private class HookDao(private val inner: FakeUserDataDao = FakeUserDataDao()) :
    com.example.foodtracker.data.journal.UserDataDao by inner {
    var onFirstOp: (suspend () -> Unit)? = null
    var failDeleteOps = false
    override suspend fun firstOp(): com.example.foodtracker.data.journal.OpEntity? {
        val head = inner.firstOp()
        onFirstOp?.let { onFirstOp = null; it() }
        return head
    }
    override suspend fun deleteOps(seqs: List<Long>) {
        if (failDeleteOps) throw IllegalStateException("disk I/O error")
        inner.deleteOps(seqs)
    }
}

private val oats = FoodItem("123", "Oats", per100g = 380, protein100g = 13f, carbs100g = 60f, fat100g = 7f)

@OptIn(ExperimentalCoroutinesApi::class)
class SyncProcessorTest {
    private val today = LocalDate.of(2026, 11, 4)
    private var clock = 1_000L
    private val dao = FakeUserDataDao()
    private val logsApi = SLogsApi()
    private val daysApi = SDaysApi()
    private val repo = JournalRepository(
        dao = dao,
        tx = object : TxRunner { override suspend fun <R> run(block: suspend () -> R): R = block() },
        logsApi = logsApi,
        daysApi = daysApi,
        scheduler = NoopScheduler(),
        now = { clock++ },
        today = { today },
    )
    private val processor = SyncProcessor(repo, logsApi, daysApi)

    @Test fun drainSendsOpsInOrder() = runTest {
        val a = repo.addLog(today, "Lunch", LoggedFood(oats, 50))
        repo.setHydration(today, 1.5f)
        val b = repo.addLog(today, "Dinner", LoggedFood(oats, 70))
        repo.setDayStatus(today, true)

        assertEquals(DrainResult.DONE, processor.drain())

        assertEquals(listOf("create:$a", "hydration:2026-11-04", "create:$b"), logsApi.calls)
        assertEquals(listOf("put:2026-11-04:true"), daysApi.calls)
        assertEquals(0, repo.pendingCount.first())
        assertNull(repo.nextOp())
    }

    @Test fun retryStopsDrainAndKeepsOrder() = runTest {
        val a = repo.addLog(today, "Lunch", LoggedFood(oats, 50))
        repo.addLog(today, "Dinner", LoggedFood(oats, 70))
        logsApi.createFailures[a] = IOException("offline")

        assertEquals(DrainResult.RETRY, processor.drain())

        assertEquals(listOf("create:$a"), logsApi.calls)
        assertEquals(2, repo.pendingCount.first())
        val head = repo.nextOp()!!
        assertEquals(a, (head.op as SyncOp.CreateLog).clientId)
        assertFalse(head.inFlight)
        assertTrue(head.attempted)
    }

    @Test fun notFoundIsDroppedSilently() = runTest {
        val a = repo.addLog(today, "Lunch", LoggedFood(oats, 50))
        logsApi.createFailures[a] = httpError(404)
        val events = mutableListOf<com.example.foodtracker.data.journal.SyncEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repo.events.collect { events += it } }

        assertEquals(DrainResult.DONE, processor.drain())

        assertNull(repo.nextOp())
        assertFalse(events.any { it is com.example.foodtracker.data.journal.SyncEvent.Rejected })
    }

    @Test fun unprocessableIsDroppedWithEvent() = runTest {
        val a = repo.addLog(today, "Lunch", LoggedFood(oats, 50))
        val b = repo.addLog(today, "Dinner", LoggedFood(oats, 70))
        logsApi.createFailures[a] = httpError(422)
        val events = mutableListOf<com.example.foodtracker.data.journal.SyncEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repo.events.collect { events += it } }

        assertEquals(DrainResult.DONE, processor.drain())

        assertEquals(listOf("create:$a", "create:$b"), logsApi.calls)
        assertNull(repo.nextOp())
        assertTrue(events.any { it is com.example.foodtracker.data.journal.SyncEvent.Rejected })
    }

    @Test fun deleteStatus404CountsAsSuccess() = runTest {
        repo.setDayStatus(today, null)
        daysApi.deleteCode = 404
        val events = mutableListOf<com.example.foodtracker.data.journal.SyncEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repo.events.collect { events += it } }

        assertEquals(DrainResult.DONE, processor.drain())

        assertEquals(listOf("delete:2026-11-04"), daysApi.calls)
        assertNull(repo.nextOp())
        assertTrue(events.any { it is com.example.foodtracker.data.journal.SyncEvent.Synced })
    }

    @Test fun unauthorizedIsRetried() = runTest {
        val a = repo.addLog(today, "Lunch", LoggedFood(oats, 50))
        logsApi.createFailures[a] = httpError(401)

        assertEquals(DrainResult.RETRY, processor.drain())

        assertEquals(1, repo.pendingCount.first())
        assertFalse(repo.nextOp()!!.inFlight)
    }

    @Test fun syncNowReturnsFalseWhenQueueNotEmptyAfterTimeout() = runTest {
        repo.addLog(today, "Lunch", LoggedFood(oats, 50))
        logsApi.hang = true

        assertFalse(processor.syncNow(5_000))
        assertEquals(1, repo.pendingCount.first())
    }

    @Test fun syncNowReturnsTrueWhenDrained() = runTest {
        repo.addLog(today, "Lunch", LoggedFood(oats, 50))
        assertTrue(processor.syncNow(5_000))
    }

    @Test fun concurrentDrainsSendEachOpOnce() = runTest {
        val a = repo.addLog(today, "Lunch", LoggedFood(oats, 50))
        val b = repo.addLog(today, "Dinner", LoggedFood(oats, 70))
        logsApi.createDelayMs = 100

        val results = listOf(async { processor.drain() }, async { processor.drain() }).awaitAll()

        assertEquals(listOf(DrainResult.DONE, DrainResult.DONE), results)
        assertEquals(listOf("create:$a", "create:$b"), logsApi.calls)
    }

    @Test fun undoBetweenReadAndSendCannotCancelClaimedCreate() = runTest {
        val dao = HookDao()
        val repo = JournalRepository(dao, MutexTx(), logsApi, daysApi, NoopScheduler(), { clock++ }, { today })
        val processor = SyncProcessor(repo, logsApi, daysApi)
        val a = repo.addLog(today, "Lunch", LoggedFood(oats, 50))
        // The user undoes the log while the processor is between reading the head and sending it.
        dao.onFirstOp = {
            backgroundScope.launch { repo.deleteLogs(listOf(a)) }
            yield()
        }

        processor.drain()
        assertEquals(DrainResult.DONE, processor.drain())

        // The create went out, so the delete must follow it: the log can't reappear on the server.
        assertEquals(listOf("create:$a", "delete:$a"), logsApi.calls)
        assertNull(repo.nextOp())
    }

    @Test fun unexpectedExceptionInDrainReturnsRetry() = runTest {
        val dao = HookDao()
        val repo = JournalRepository(dao, MutexTx(), logsApi, daysApi, NoopScheduler(), { clock++ }, { today })
        val processor = SyncProcessor(repo, logsApi, daysApi)
        repo.addLog(today, "Lunch", LoggedFood(oats, 50))
        dao.failDeleteOps = true

        assertEquals(DrainResult.RETRY, processor.drain())
        assertFalse(repo.nextOp()!!.inFlight)
        assertFalse(processor.syncNow(5_000))

        dao.failDeleteOps = false
        assertEquals(DrainResult.DONE, processor.drain())
        assertNull(repo.nextOp())
    }

    @Test fun drainRefusesWhenSignedInUidDiffersFromOwner() = runTest {
        var uid: String? = "uid-a"
        val repo = JournalRepository(
            FakeUserDataDao(), MutexTx(), logsApi, daysApi, NoopScheduler(), { clock++ }, { today },
            meta = com.example.foodtracker.data.journal.InMemoryJournalMeta(),
            currentUid = { uid },
        )
        val processor = SyncProcessor(repo, logsApi, daysApi)
        repo.addLog(today, "Lunch", LoggedFood(oats, 50))
        assertEquals("uid-a", repo.ownerUid())

        uid = "uid-b"
        assertEquals(DrainResult.DONE, processor.drain())
        assertTrue(logsApi.calls.isEmpty())
        assertEquals(1, repo.pendingCount.first())

        uid = null
        assertEquals(DrainResult.DONE, processor.drain())
        assertTrue(logsApi.calls.isEmpty())

        uid = "uid-a"
        assertEquals(DrainResult.DONE, processor.drain())
        assertEquals(1, logsApi.calls.size)
    }
}
