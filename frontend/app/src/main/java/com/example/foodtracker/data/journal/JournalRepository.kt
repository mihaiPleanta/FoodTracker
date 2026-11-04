package com.example.foodtracker.data.journal

import com.example.foodtracker.api.DaysApi
import com.example.foodtracker.api.LogsApi
import com.example.foodtracker.model.DayStatus
import com.example.foodtracker.model.FoodLogDto
import com.example.foodtracker.model.LoggedFood
import com.example.foodtracker.model.MEAL_ORDER
import com.example.foodtracker.model.mealKeyToBackend
import com.example.foodtracker.model.mealNameFromBackend
import com.example.foodtracker.model.toDayStatus
import com.example.foodtracker.sync.LocalDayValues
import com.example.foodtracker.sync.LocalLog
import com.example.foodtracker.sync.QueuedOp
import com.example.foodtracker.sync.SyncOp
import com.example.foodtracker.sync.SyncOutcome
import com.example.foodtracker.sync.SyncScheduler
import com.example.foodtracker.sync.date
import com.example.foodtracker.sync.dropDependents
import com.example.foodtracker.sync.enqueue
import com.example.foodtracker.sync.mergeServerDay
import com.example.foodtracker.sync.mergeServerDayValues
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** Local window: today and the 61 days before it. */
const val MIRROR_DAYS = 62
/** Weights: the window + 60 days of EMA warm-up. */
const val WEIGHT_MIRROR_DAYS = 122
private const val REFRESH_RETRIES = 2

data class LocalDay(
    val date: LocalDate,
    /** key = "Breakfast"/"Lunch"/"Dinner"/"Snacks" (all four, in [MEAL_ORDER]); lists ordered by sortKey. */
    val meals: Map<String, List<LoggedFood>>,
    val hydrationL: Float,
    val status: DayStatus?,
    val weightKg: Float?,
    val fetched: Boolean,
)

sealed interface SyncEvent {
    data class Synced(val dates: Set<String>) : SyncEvent
    data object Rejected : SyncEvent
}

/**
 * Single source for the user's journal: a Room mirror the UI observes plus a queue of ops
 * pushed by the sync worker. Every local write updates the mirror and the queue in one transaction.
 */
class JournalRepository(
    private val dao: UserDataDao,
    private val tx: TxRunner,
    private val logsApi: LogsApi,
    @Suppress("unused") private val daysApi: DaysApi,
    private val scheduler: SyncScheduler,
    private val now: () -> Long,
    private val today: () -> LocalDate,
) {
    /** Bumped (inside the tx) by completeOp / rejectOp / clearAll; guards refreshes fetched outside the tx. */
    private val epoch = AtomicLong()
    /** Bumped by clearAll only: a refresh started before it never writes. */
    private val clears = AtomicLong()
    private val _events = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<SyncEvent> = _events.asSharedFlow()
    val pendingCount: Flow<Int> = dao.observeOpCount()

    // ── Observation ──

    fun observeDay(date: LocalDate): Flow<LocalDay> {
        val key = date.toString()
        return combine(dao.observeLogs(key), dao.observeDay(key), dao.observeWeight(key)) { logs, day, weight ->
            val byMeal = logs.map { it.toLocal().logged to mealNameFromBackend(it.meal) }
                .groupBy({ it.second }, { it.first })
            LocalDay(
                date = date,
                meals = MEAL_ORDER.associateWith { byMeal[it].orEmpty() },
                hydrationL = day?.hydrationL ?: 0f,
                status = day?.status.toDayStatus(),
                weightKg = weight?.weightKg,
                fetched = day?.fetchedAt != null,
            )
        }
    }

    fun observeWeights(): Flow<List<Pair<LocalDate, Float>>> =
        dao.observeWeights().map { list -> list.map { LocalDate.parse(it.date) to it.weightKg } }

    // ── Server refresh ──

    /** GET /days/{d}; throws IOException / HttpException. */
    suspend fun refreshDay(date: LocalDate) {
        val key = date.toString()
        guardedRefresh(fetch = { logsApi.getDay(key) }) { resp ->
            val pending = pendingOps()
            // GET /days/{d} carries no goals snapshot: keep the local one while the day stays marked.
            val goals = if (resp.status == null) null else JournalJson.decodeGoals(dao.getDay(key)?.goalsJson)
            val values = LocalDayValues(resp.hydrationLiters, resp.status.toDayStatus(), goals)
            applyServerDay(key, resp.foodsByMeal.values.flatten(), values, pending)
            applyServerWeight(key, resp.weightCheckIn?.weightKg, pending)
        }
    }

    /** Downloads the whole mirror window (+ weights for the EMA warm-up). Days absent on the server become empty. */
    suspend fun refreshWindow() {
        val t = today()
        val from = t.minusDays(MIRROR_DAYS - 1L)
        val weightFrom = t.minusDays(WEIGHT_MIRROR_DAYS - 1L)
        guardedRefresh(fetch = {
            val days = logsApi.getDays(from.toString(), t.toString()).days.associateBy { it.date }
            val weights = logsApi.getWeightCheckIns(weightFrom.toString(), t.toString()).items
                .associate { it.date to it.weightKg }
            days to weights
        }) { (days, weights) ->
            val pending = pendingOps()
            for (d in datesBetween(from, t)) {
                val key = d.toString()
                val item = days[key]
                val values = LocalDayValues(item?.hydrationLiters ?: 0f, item?.status.toDayStatus(), item?.goals)
                applyServerDay(key, item?.foodsByMeal?.values?.flatten().orEmpty(), values, pending)
            }
            for (d in datesBetween(weightFrom, t)) applyServerWeight(d.toString(), weights[d.toString()], pending)
        }
    }

    /** True when every day of [from]..[to] is inside the mirror window and was downloaded. */
    suspend fun isCovered(from: LocalDate, to: LocalDate): Boolean {
        val t = today()
        if (from.isAfter(to) || from.isBefore(t.minusDays(MIRROR_DAYS - 1L)) || to.isAfter(t)) return false
        val fetched = dao.getDays(from.toString(), to.toString()).count { it.fetchedAt != null }
        return fetched.toLong() == ChronoUnit.DAYS.between(from, to) + 1
    }

    // ── Local writes ──

    suspend fun addLog(date: LocalDate, mealName: String, logged: LoggedFood, sortKey: Long? = null): String {
        val cid = UUID.randomUUID().toString()
        val key = date.toString()
        val meal = mealKeyToBackend(mealName)
        write {
            dao.upsertLogs(listOf(LogEntity(cid, null, key, meal, sortKey ?: now(), JournalJson.encodeLogged(logged), true)))
            enqueueOp(SyncOp.CreateLog(cid, logged.toCreateBody(key, meal, cid)), key)
        }
        return cid
    }

    suspend fun updateLog(clientId: String, logged: LoggedFood) {
        val changed = tx.run {
            val row = dao.getLog(clientId) ?: return@run false
            dao.upsertLogs(listOf(row.copy(payloadJson = JournalJson.encodeLogged(logged), pending = true)))
            enqueueOp(SyncOp.UpdateLog(clientId, logged.toUpdateBody()), row.date)
            true
        }
        if (changed) scheduler.requestSync()
    }

    suspend fun deleteLogs(clientIds: Collection<String>) {
        val changed = tx.run {
            val rows = clientIds.mapNotNull { dao.getLog(it) }
            rows.forEach { enqueueOp(SyncOp.DeleteLog(it.clientId), it.date) }
            dao.deleteLogs(rows.map { it.clientId })
            rows.isNotEmpty()
        }
        if (changed) scheduler.requestSync()
    }

    /** [items] = (mealName, source log as edited in the copy sheet). Returns the new clientIds, in order. */
    suspend fun copyLogs(toDate: LocalDate, items: List<Pair<String, LoggedFood>>): List<String> {
        if (items.isEmpty()) return emptyList()
        val key = toDate.toString()
        val bodies = items.map { (mealName, logged) ->
            logged.toCopyBody(key, mealKeyToBackend(mealName), UUID.randomUUID().toString())
        }
        val base = now()
        write {
            bodies.forEachIndexed { i, body ->
                val cid = body.clientId!!
                dao.upsertLogs(listOf(LogEntity(cid, null, key, body.meal, base + i, JournalJson.encodeLogged(body.toLoggedFood()), true)))
                enqueueOp(SyncOp.CreateLog(cid, body), key)
            }
        }
        return bodies.map { it.clientId!! }
    }

    suspend fun setHydration(date: LocalDate, liters: Float) {
        val key = date.toString()
        write {
            dao.upsertDays(listOf(dayOrEmpty(key).copy(hydrationL = liters)))
            enqueueOp(SyncOp.PutHydration(key, liters), key)
        }
    }

    suspend fun setWeight(date: LocalDate, weightKg: Float) {
        val key = date.toString()
        write {
            dao.upsertWeights(listOf(WeightEntity(key, weightKg, pending = true)))
            enqueueOp(SyncOp.PutWeight(key, weightKg), key)
        }
    }

    /** [complete] == null clears the mark (and the goals snapshot). */
    suspend fun setDayStatus(date: LocalDate, complete: Boolean?) {
        val key = date.toString()
        write {
            val day = dayOrEmpty(key)
            val status = complete?.let { if (it) DayStatus.COMPLETE else DayStatus.INCOMPLETE }
            dao.upsertDays(listOf(day.copy(status = status.toWire(), goalsJson = if (complete == null) null else day.goalsJson)))
            enqueueOp(SyncOp.PutDayStatus(key, complete), key)
        }
    }

    suspend fun hasLocalLog(date: LocalDate): Boolean = dao.countLogs(date.toString()) > 0

    /** True while a locally written weight for [date] hasn't been accepted by the server. */
    suspend fun isWeightPending(date: LocalDate): Boolean = dao.getWeight(date.toString())?.pending == true

    suspend fun clearAll() {
        tx.run {
            clears.incrementAndGet()
            epoch.incrementAndGet()
            dao.clearLogs()
            dao.clearDays()
            dao.clearWeights()
            dao.clearOps()
            dao.clearQuickPicks()
        }
    }

    // ── Used by the sync processor ──

    /** Head of the queue (lowest seq), whether in flight or not. */
    suspend fun nextOp(): QueuedOp? = dao.firstOp()?.let(JournalJson::toQueued)

    suspend fun markInFlight(seq: Long) = dao.markInFlight(seq)

    suspend fun releaseInFlight() = dao.releaseInFlight()

    suspend fun completeOp(op: QueuedOp, response: FoodLogDto?) {
        val date = tx.run {
            epoch.incrementAndGet()
            val entity = dao.getOp(op.seq) ?: return@run null
            dao.deleteOps(listOf(op.seq))
            val remaining = pendingOps()
            when (val o = op.op) {
                is SyncOp.CreateLog -> settleLog(o.clientId, response, remaining)
                is SyncOp.UpdateLog -> settleLog(o.clientId, response, remaining)
                is SyncOp.PutWeight -> if (remaining.none { it is SyncOp.PutWeight && it.date == o.date }) {
                    dao.getWeight(o.date)?.let { dao.upsertWeights(listOf(it.copy(pending = false))) }
                }
                else -> Unit
            }
            entity.date
        } ?: return
        _events.emit(SyncEvent.Synced(setOf(date)))
    }

    suspend fun rejectOp(op: QueuedOp, outcome: SyncOutcome) {
        val date = tx.run {
            epoch.incrementAndGet()
            val rows = dao.getOps()
            val date = rows.firstOrNull { it.seq == op.seq }?.date ?: op.op.date
            val kept = dropDependents(rows.map(JournalJson::toQueued), op).mapTo(HashSet()) { it.seq }
            dao.deleteOps(rows.map { it.seq }.filter { it !in kept })
            // The server never accepted the create: drop its mirror row now, even if the refresh below fails.
            (op.op as? SyncOp.CreateLog)?.let { dao.deleteLogs(listOf(it.clientId)) }
            date
        }
        if (date != null) {
            try {
                refreshDay(LocalDate.parse(date))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Offline: the mirror converges at the next refresh.
            }
        }
        if (outcome == SyncOutcome.DROP_NOTIFY) _events.emit(SyncEvent.Rejected)
    }

    // ── Internals ──

    private enum class RefreshResult { APPLIED, RETRY, DROPPED }

    /**
     * Fetches outside the transaction and applies inside it only if no op completed/was rejected
     * meanwhile ([epoch]); otherwise re-fetches (max [REFRESH_RETRIES] times) then skips silently.
     * After a [clearAll] ([clears]) the snapshot belongs to the previous session and is dropped.
     */
    private suspend fun <T> guardedRefresh(fetch: suspend () -> T, apply: suspend (T) -> Unit) {
        repeat(REFRESH_RETRIES + 1) {
            val seenEpoch = epoch.get()
            val seenClears = clears.get()
            val data = fetch()
            val result = tx.run {
                when {
                    clears.get() != seenClears -> RefreshResult.DROPPED
                    epoch.get() != seenEpoch -> RefreshResult.RETRY
                    else -> { apply(data); RefreshResult.APPLIED }
                }
            }
            if (result != RefreshResult.RETRY) return
        }
    }

    private suspend fun write(block: suspend () -> Unit) {
        tx.run { block() }
        scheduler.requestSync()
    }

    private suspend fun pendingOps(): List<SyncOp> = dao.getOps().map { JournalJson.decodeOp(it.json) }

    private suspend fun dayOrEmpty(key: String) = dao.getDay(key) ?: DayEntity(key, 0f, null, null, null)

    /** Persists `enqueue(queue, op)`: seq = 0 entries are inserted, missing ones deleted, changed ones updated. */
    private suspend fun enqueueOp(op: SyncOp, date: String) {
        val rows = dao.getOps()
        val next = enqueue(rows.map(JournalJson::toQueued), op)
        val bySeq = next.filter { it.seq != 0L }.associateBy { it.seq }
        dao.deleteOps(rows.map { it.seq }.filter { it !in bySeq })
        rows.forEach { row ->
            val kept = bySeq[row.seq] ?: return@forEach
            val json = JournalJson.encodeOp(kept.op)
            if (json != row.json) dao.updateOp(row.copy(json = json))
        }
        next.filter { it.seq == 0L }.forEach { dao.insertOp(OpEntity(date = date, json = JournalJson.encodeOp(it.op))) }
    }

    /** Server row accepted: record its id; the mirror is clean only when no other op touches the log. */
    private suspend fun settleLog(clientId: String, response: FoodLogDto?, remaining: List<SyncOp>) {
        val row = dao.getLog(clientId) ?: return
        val serverId = response?.id ?: row.serverId
        val settled = if (remaining.none { it.logClientId() == clientId }) {
            row.copy(
                serverId = serverId,
                pending = false,
                payloadJson = response?.let { JournalJson.encodeLogged(it.toLogged()) } ?: row.payloadJson,
            )
        } else {
            row.copy(serverId = serverId)
        }
        dao.upsertLogs(listOf(settled))
    }

    private suspend fun applyServerDay(key: String, serverLogs: List<FoodLogDto>, server: LocalDayValues, pending: List<SyncOp>) {
        val local = dao.getLogs(key).map { it.toLocal() }
        val localById = local.associateBy { it.clientId }
        // Rows we already know keep their local position.
        val fromServer = serverLogs.map { it.toLocal() }.map { s ->
            localById[s.clientId]?.let { l -> s.copy(sortKey = l.sortKey, logged = s.logged.copy(sortKey = l.sortKey)) } ?: s
        }
        val merged: List<LocalLog> = mergeServerDay(fromServer, local, pending)
        val keep = merged.mapTo(HashSet()) { it.clientId }
        dao.deleteLogs(local.map { it.clientId }.filter { it !in keep })
        dao.upsertLogs(merged.map { it.toEntity() })

        val prev = dao.getDay(key)
        val localValues = prev?.let { LocalDayValues(it.hydrationL, it.status.toDayStatus(), JournalJson.decodeGoals(it.goalsJson)) }
        val v = mergeServerDayValues(server, localValues, key, pending)
        dao.upsertDays(listOf(DayEntity(key, v.hydrationL, v.status.toWire(), JournalJson.encodeGoals(v.goals), now())))
    }

    private suspend fun applyServerWeight(key: String, serverKg: Float?, pending: List<SyncOp>) {
        if (pending.any { it is SyncOp.PutWeight && it.date == key }) return
        if (serverKg == null) dao.deleteWeights(listOf(key))
        else dao.upsertWeights(listOf(WeightEntity(key, serverKg, pending = false)))
    }
}

private fun SyncOp.logClientId(): String? = when (this) {
    is SyncOp.CreateLog -> clientId
    is SyncOp.UpdateLog -> clientId
    is SyncOp.DeleteLog -> clientId
    else -> null
}

private fun DayStatus?.toWire(): String? = when (this) {
    DayStatus.COMPLETE -> "complete"
    DayStatus.INCOMPLETE -> "incomplete"
    null -> null
}

private fun datesBetween(from: LocalDate, to: LocalDate): Sequence<LocalDate> =
    generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }
