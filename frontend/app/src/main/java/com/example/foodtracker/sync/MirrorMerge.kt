package com.example.foodtracker.sync

import com.example.foodtracker.model.DayStatus
import com.example.foodtracker.model.LoggedFood
import com.example.foodtracker.model.StatsGoalsDto

data class LocalLog(
    val clientId: String,
    val serverId: Long?,
    val date: String,
    val meal: String,
    val sortKey: Long,
    val logged: LoggedFood,
    val pending: Boolean,
)

data class LocalDayValues(val hydrationL: Float, val status: DayStatus?, val goals: StatsGoalsDto?)

private fun LocalLog.withPending(p: Boolean) = copy(pending = p, logged = logged.copy(pending = p))

/** Mirror of a day after the server answer, respecting ops still queued. */
fun mergeServerDay(server: List<LocalLog>, local: List<LocalLog>, pending: List<SyncOp>): List<LocalLog> {
    val deleted = pending.filterIsInstance<SyncOp.DeleteLog>().map { it.clientId }.toSet()
    val touched = buildSet {
        pending.forEach {
            when (it) {
                is SyncOp.UpdateLog -> add(it.clientId)
                is SyncOp.CreateLog -> add(it.clientId)
                else -> Unit
            }
        }
    }
    val localById = local.associateBy { it.clientId }
    val serverIds = server.mapTo(HashSet()) { it.clientId }

    val fromServer = server.filter { it.clientId !in deleted }.map { s ->
        val l = localById[s.clientId]
        if (s.clientId in touched && l != null) l.withPending(true) else s.withPending(false)
    }
    val localOnly = local.filter { it.pending && it.clientId !in serverIds && it.clientId !in deleted }
        .map { it.withPending(true) }
    return (fromServer + localOnly).sortedBy { it.sortKey }
}

fun mergeServerDayValues(
    server: LocalDayValues,
    local: LocalDayValues?,
    date: String,
    pending: List<SyncOp>,
): LocalDayValues {
    if (local == null) return server
    val hasHydration = pending.any { it is SyncOp.PutHydration && it.date == date }
    val hasStatus = pending.any { it is SyncOp.PutDayStatus && it.date == date }
    return LocalDayValues(
        hydrationL = if (hasHydration) local.hydrationL else server.hydrationL,
        status = if (hasStatus) local.status else server.status,
        goals = if (hasStatus) local.goals else server.goals,
    )
}
