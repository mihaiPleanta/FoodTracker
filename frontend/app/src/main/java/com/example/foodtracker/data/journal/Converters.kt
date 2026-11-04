package com.example.foodtracker.data.journal

import com.example.foodtracker.model.LoggedFood
import com.example.foodtracker.model.StatsGoalsDto
import com.example.foodtracker.sync.QueuedOp
import com.example.foodtracker.sync.SyncOp
import com.google.gson.Gson
import com.google.gson.JsonObject

/** Gson codecs for the JSON columns of [UserDataDb]. Ops use `{"type": "...", "data": {...}}`. */
internal object JournalJson {
    private val gson = Gson()

    fun encodeLogged(logged: LoggedFood): String =
        gson.toJson(logged.copy(id = null, clientId = null, pending = false, sortKey = null))

    fun decodeLogged(json: String): LoggedFood = gson.fromJson(json, LoggedFood::class.java)

    fun encodeGoals(goals: StatsGoalsDto?): String? = goals?.let { gson.toJson(it) }

    fun decodeGoals(json: String?): StatsGoalsDto? = json?.let { gson.fromJson(it, StatsGoalsDto::class.java) }

    fun encodeOp(op: SyncOp): String {
        val type = when (op) {
            is SyncOp.CreateLog -> "CreateLog"
            is SyncOp.UpdateLog -> "UpdateLog"
            is SyncOp.DeleteLog -> "DeleteLog"
            is SyncOp.PutHydration -> "PutHydration"
            is SyncOp.PutWeight -> "PutWeight"
            is SyncOp.PutDayStatus -> "PutDayStatus"
        }
        return JsonObject().apply {
            addProperty("type", type)
            add("data", gson.toJsonTree(op))
        }.toString()
    }

    fun decodeOp(json: String): SyncOp {
        val obj = gson.fromJson(json, JsonObject::class.java)
        val cls: Class<out SyncOp> = when (val type = obj.get("type").getAsString()) {
            "CreateLog" -> SyncOp.CreateLog::class.java
            "UpdateLog" -> SyncOp.UpdateLog::class.java
            "DeleteLog" -> SyncOp.DeleteLog::class.java
            "PutHydration" -> SyncOp.PutHydration::class.java
            "PutWeight" -> SyncOp.PutWeight::class.java
            "PutDayStatus" -> SyncOp.PutDayStatus::class.java
            else -> error("Unknown op type $type")
        }
        return gson.fromJson(obj.get("data"), cls)
    }

    fun toQueued(e: OpEntity) = QueuedOp(e.seq, decodeOp(e.json), e.inFlight, e.attempted)
}
