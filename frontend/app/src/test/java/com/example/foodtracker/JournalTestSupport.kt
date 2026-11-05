package com.example.foodtracker

import com.example.foodtracker.api.DaysApi
import com.example.foodtracker.api.LogsApi
import com.example.foodtracker.data.journal.FakeSyncScheduler
import com.example.foodtracker.data.journal.FakeUserDataDao
import com.example.foodtracker.data.journal.InMemoryJournalMeta
import com.example.foodtracker.data.journal.JournalJson
import com.example.foodtracker.data.journal.JournalRepository
import com.example.foodtracker.data.journal.TxRunner
import com.example.foodtracker.model.DayResponseDto
import com.example.foodtracker.model.DayStatusResponseDto
import com.example.foodtracker.model.DayStatusUpdateDto
import com.example.foodtracker.model.DaysRangeResponseDto
import com.example.foodtracker.model.FoodLogCopyRequestDto
import com.example.foodtracker.model.FoodLogCreateDto
import com.example.foodtracker.model.FoodLogDeleteBatchDto
import com.example.foodtracker.model.FoodLogDeleteBatchResultDto
import com.example.foodtracker.model.FoodLogDto
import com.example.foodtracker.model.FoodLogSummaryDto
import com.example.foodtracker.model.FoodLogUpdateDto
import com.example.foodtracker.model.HydrationDto
import com.example.foodtracker.model.HydrationUpdateDto
import com.example.foodtracker.model.StatsDaysResponseDto
import com.example.foodtracker.model.WeightCheckInCreateDto
import com.example.foodtracker.model.WeightCheckInDto
import com.example.foodtracker.model.WeightCheckInListDto
import com.example.foodtracker.sync.SyncOp
import retrofit2.Response
import java.time.LocalDate

/**
 * Server side of the journal for view-model tests: only the reads the mirror refresh uses.
 * Writes never reach it — they are queued in the mirror and pushed by the sync worker.
 */
class JournalFakeLogsApi : LogsApi {
    val days = mutableMapOf<String, DayResponseDto>()
    val dayRequests = mutableListOf<String>()
    /** When set, every GET /days/{d} throws it (e.g. IOException = offline). */
    var dayFailure: Throwable? = null

    override suspend fun getDay(date: String): DayResponseDto {
        dayRequests += date
        dayFailure?.let { throw it }
        return days[date] ?: DayResponseDto(emptyMap(), 0f, null)
    }
    override suspend fun getDays(from: String, to: String): DaysRangeResponseDto = DaysRangeResponseDto(emptyList())
    override suspend fun getWeightCheckIns(from: String, to: String): WeightCheckInListDto = WeightCheckInListDto(emptyList())

    override suspend fun createFoodLog(body: FoodLogCreateDto): FoodLogDto = error("writes go through the queue")
    override suspend fun updateFoodLog(id: Long, body: FoodLogUpdateDto): FoodLogDto = error("writes go through the queue")
    override suspend fun updateFoodLogByClient(cid: String, body: FoodLogUpdateDto): FoodLogDto = error("writes go through the queue")
    override suspend fun deleteFoodLogByClient(cid: String): Response<Unit> = error("writes go through the queue")
    override suspend fun deleteFoodLog(id: Long): Response<Unit> = error("writes go through the queue")
    override suspend fun deleteFoodLogs(body: FoodLogDeleteBatchDto): FoodLogDeleteBatchResultDto = error("writes go through the queue")
    override suspend fun copyFoodLogs(body: FoodLogCopyRequestDto): List<FoodLogDto> = error("unused")
    override suspend fun getFoodLogSummary(from: String, to: String): FoodLogSummaryDto = error("unused")
    override suspend fun putHydration(date: String, body: HydrationUpdateDto): HydrationDto = error("writes go through the queue")
    override suspend fun postWeightCheckIn(body: WeightCheckInCreateDto): WeightCheckInDto = error("writes go through the queue")
}

object JournalFakeDaysApi : DaysApi {
    override suspend fun putStatus(date: String, body: DayStatusUpdateDto): DayStatusResponseDto = error("writes go through the queue")
    override suspend fun deleteStatus(date: String): Response<Unit> = error("writes go through the queue")
    override suspend fun getStatsDays(from: String, to: String): StatsDaysResponseDto = error("unused")
}

/** A real [JournalRepository] over in-memory fakes. */
internal class TestJournal(
    val logsApi: JournalFakeLogsApi = JournalFakeLogsApi(),
    today: () -> LocalDate = { LocalDate.now() },
) {
    val dao = FakeUserDataDao()
    val meta = InMemoryJournalMeta()
    /** Signed-in account seen by the journal. */
    var uid: String? = null
    val scheduler = FakeSyncScheduler()
    private var clock = 1_000_000L
    /** When true every transaction throws, as a failing Room database would. */
    var failWrites = false
    val repo = JournalRepository(
        dao = dao,
        tx = object : TxRunner {
            override suspend fun <R> run(block: suspend () -> R): R {
                if (failWrites) throw IllegalStateException("disk full")
                return block()
            }
        },
        logsApi = logsApi,
        daysApi = JournalFakeDaysApi,
        scheduler = scheduler,
        now = { clock++ },
        today = today,
        meta = meta,
        currentUid = { uid },
    )

    /** The queued ops, in order. */
    fun ops(): List<SyncOp> = dao.ops.value.map { JournalJson.toQueued(it).op }
}

fun serverLog(id: Long, date: String, meal: String = "BREAKFAST", name: String = "Food $id", grams: Int = 100) = FoodLogDto(
    id = id, clientId = "srv-cid-$id", logDate = date, meal = meal, grams = grams,
    barcode = "b$id", name = name, kcal100g = 100f, protein100g = 5f, carbs100g = 10f, fat100g = 2f,
)
