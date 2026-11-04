package com.example.foodtracker.data.journal

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [UserDataDao] with the same ordering semantics as the Room queries. */
class FakeUserDataDao : UserDataDao {
    val logs = MutableStateFlow<Map<String, LogEntity>>(emptyMap())
    val days = MutableStateFlow<Map<String, DayEntity>>(emptyMap())
    val weights = MutableStateFlow<Map<String, WeightEntity>>(emptyMap())
    val ops = MutableStateFlow<List<OpEntity>>(emptyList())
    val quickPicks = MutableStateFlow<Map<String, QuickPickEntity>>(emptyMap())
    private var nextSeq = 1L

    private val logOrder = compareBy<LogEntity>({ it.sortKey }, { it.serverId })

    override fun observeLogs(date: String): Flow<List<LogEntity>> =
        logs.map { m -> m.values.filter { it.date == date }.sortedWith(logOrder) }
    override fun observeDay(date: String): Flow<DayEntity?> = days.map { it[date] }
    override fun observeWeight(date: String): Flow<WeightEntity?> = weights.map { it[date] }
    override fun observeWeights(): Flow<List<WeightEntity>> = weights.map { m -> m.values.sortedBy { it.date } }
    override fun observeOpCount(): Flow<Int> = ops.map { it.size }

    override suspend fun getLogs(date: String): List<LogEntity> =
        logs.value.values.filter { it.date == date }.sortedWith(logOrder)
    override suspend fun getLog(clientId: String): LogEntity? = logs.value[clientId]
    override suspend fun getLogsBetween(from: String, to: String): List<LogEntity> =
        logs.value.values.filter { it.date in from..to }.sortedWith(compareBy<LogEntity>({ it.date }, { it.sortKey }, { it.serverId }))
    override suspend fun countLogs(date: String): Int = logs.value.values.count { it.date == date }
    override suspend fun upsertLogs(logs: List<LogEntity>) {
        this.logs.value = this.logs.value + logs.associateBy { it.clientId }
    }
    override suspend fun deleteLogs(clientIds: List<String>) {
        logs.value = logs.value - clientIds.toSet()
    }

    override suspend fun getDay(date: String): DayEntity? = days.value[date]
    override suspend fun getDays(from: String, to: String): List<DayEntity> =
        days.value.values.filter { it.date in from..to }.sortedBy { it.date }
    override suspend fun upsertDays(days: List<DayEntity>) {
        this.days.value = this.days.value + days.associateBy { it.date }
    }

    override suspend fun getWeight(date: String): WeightEntity? = weights.value[date]
    override suspend fun getWeightsBetween(from: String, to: String): List<WeightEntity> =
        weights.value.values.filter { it.date in from..to }.sortedBy { it.date }
    override suspend fun upsertWeights(weights: List<WeightEntity>) {
        this.weights.value = this.weights.value + weights.associateBy { it.date }
    }
    override suspend fun deleteWeights(dates: List<String>) {
        weights.value = weights.value - dates.toSet()
    }

    override suspend fun getOps(): List<OpEntity> = ops.value
    override suspend fun firstOp(): OpEntity? = ops.value.firstOrNull()
    override suspend fun getOp(seq: Long): OpEntity? = ops.value.firstOrNull { it.seq == seq }
    override suspend fun insertOp(op: OpEntity): Long {
        val seq = nextSeq++
        ops.value = ops.value + op.copy(seq = seq)
        return seq
    }
    override suspend fun updateOp(op: OpEntity) {
        ops.value = ops.value.map { if (it.seq == op.seq) op else it }
    }
    override suspend fun deleteOps(seqs: List<Long>) {
        ops.value = ops.value.filterNot { it.seq in seqs }
    }
    override suspend fun markInFlight(seq: Long) {
        ops.value = ops.value.map { if (it.seq == seq) it.copy(inFlight = true, attempted = true) else it }
    }
    override suspend fun releaseInFlight() {
        ops.value = ops.value.map { it.copy(inFlight = false) }
    }

    override suspend fun getQuickPick(key: String): QuickPickEntity? = quickPicks.value[key]
    override suspend fun upsertQuickPick(entity: QuickPickEntity) {
        quickPicks.value = quickPicks.value + (entity.key to entity)
    }

    override suspend fun clearLogs() { logs.value = emptyMap() }
    override suspend fun clearDays() { days.value = emptyMap() }
    override suspend fun clearWeights() { weights.value = emptyMap() }
    override suspend fun clearOps() { ops.value = emptyList() }
    override suspend fun clearQuickPicks() { quickPicks.value = emptyMap() }
}
