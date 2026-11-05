package com.example.foodtracker.sync

import com.example.foodtracker.api.DaysApi
import com.example.foodtracker.api.LogsApi
import android.util.Log
import com.example.foodtracker.data.journal.JournalRepository
import com.example.foodtracker.model.DayStatusUpdateDto
import com.example.foodtracker.model.FoodLogDto
import com.example.foodtracker.model.HydrationUpdateDto
import com.example.foodtracker.model.WeightCheckInCreateDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

enum class DrainResult { DONE, RETRY }

/** Pushes the op queue to the server, strictly in order. Concurrent [drain] calls are serialized. */
class SyncProcessor(
    private val repo: JournalRepository,
    private val logsApi: LogsApi,
    private val daysApi: DaysApi,
) {
    private val lock = Mutex()

    private class Sent(val code: Int?, val log: FoodLogDto? = null)

    suspend fun drain(): DrainResult = lock.withLock { drainLocked() }

    /**
     * Never throws except [CancellationException]: any other failure (Room, mapping...) releases
     * the claimed op and asks for a retry. Refuses to send another account's queue.
     */
    private suspend fun drainLocked(): DrainResult = try {
        if (repo.ownerMismatch()) {
            DrainResult.DONE
        } else {
            // An op left "in flight" by a crash, a killed process or a timed-out drain is sent again.
            repo.releaseInFlight()
            sendAll()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        try { Log.w("SyncProcessor", "drain failed", e) } catch (_: Throwable) { }
        try { repo.releaseInFlight() } catch (c: CancellationException) { throw c } catch (_: Exception) { }
        DrainResult.RETRY
    }

    private suspend fun sendAll(): DrainResult {
        while (true) {
            val queued = repo.claimNextOp() ?: return DrainResult.DONE
            val sent = send(queued.op)
            when (val outcome = classify(sent.code)) {
                SyncOutcome.SUCCESS -> repo.completeOp(queued, sent.log)
                SyncOutcome.DROP_SILENT, SyncOutcome.DROP_NOTIFY -> repo.rejectOp(queued, outcome)
                SyncOutcome.RETRY -> {
                    repo.releaseInFlight()
                    return DrainResult.RETRY
                }
            }
        }
    }

    /** For logout: drains for at most [timeoutMs]; true when the queue is empty afterwards. Never throws but cancellation. */
    suspend fun syncNow(timeoutMs: Long): Boolean {
        withTimeoutOrNull(timeoutMs) { drain() }
        return try {
            repo.nextOp() == null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun send(op: SyncOp): Sent = when (op) {
        is SyncOp.CreateLog -> call { Sent(200, logsApi.createFoodLog(op.body)) }
        is SyncOp.UpdateLog -> call { Sent(200, logsApi.updateFoodLogByClient(op.clientId, op.body)) }
        is SyncOp.DeleteLog -> raw { logsApi.deleteFoodLogByClient(op.clientId) }
        is SyncOp.PutHydration -> call { logsApi.putHydration(op.date, HydrationUpdateDto(op.liters)); Sent(200) }
        is SyncOp.PutWeight -> call { logsApi.postWeightCheckIn(WeightCheckInCreateDto(op.date, op.weightKg)); Sent(200) }
        is SyncOp.PutDayStatus -> when (val complete = op.complete) {
            null -> raw(treatNotFoundAsDone = true) { daysApi.deleteStatus(op.date) }
            else -> call { daysApi.putStatus(op.date, DayStatusUpdateDto(complete)); Sent(200) }
        }
    }

    private suspend fun call(block: suspend () -> Sent): Sent = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        Sent(e.code())
    } catch (_: IOException) {
        Sent(null)
    }

    /** DELETE-style calls returning a [Response]; a missing target (404) already means the desired state. */
    private suspend fun raw(treatNotFoundAsDone: Boolean = false, block: suspend () -> Response<Unit>): Sent {
        val sent = call { Sent(block().code()) }
        return if (treatNotFoundAsDone && sent.code == 404) Sent(200) else sent
    }
}
