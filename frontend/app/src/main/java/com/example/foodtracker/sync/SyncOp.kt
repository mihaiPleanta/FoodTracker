package com.example.foodtracker.sync

import com.example.foodtracker.model.FoodLogCreateDto
import com.example.foodtracker.model.FoodLogUpdateDto

sealed interface SyncOp {
    data class CreateLog(val clientId: String, val body: FoodLogCreateDto) : SyncOp
    data class UpdateLog(val clientId: String, val body: FoodLogUpdateDto) : SyncOp
    data class DeleteLog(val clientId: String) : SyncOp
    data class PutHydration(val date: String, val liters: Float) : SyncOp
    data class PutWeight(val date: String, val weightKg: Float) : SyncOp
    /** [complete] == null means DELETE the status. */
    data class PutDayStatus(val date: String, val complete: Boolean?) : SyncOp
}

/** CreateLog -> body.logDate, PutX -> date, Update/Delete -> null (resolved from the mirror). */
val SyncOp.date: String?
    get() = when (this) {
        is SyncOp.CreateLog -> body.logDate
        is SyncOp.PutHydration -> date
        is SyncOp.PutWeight -> date
        is SyncOp.PutDayStatus -> date
        is SyncOp.UpdateLog, is SyncOp.DeleteLog -> null
    }

data class QueuedOp(val seq: Long, val op: SyncOp, val inFlight: Boolean = false,
    /** Set when the op was ever sent (never cleared): the server may already hold its effect. */
    val attempted: Boolean = false,
)
