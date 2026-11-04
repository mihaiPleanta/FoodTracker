package com.example.foodtracker.data.journal

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One food log in the local mirror. [meal] is the backend enum (BREAKFAST…). */
@Entity(tableName = "logs", indices = [Index("date")])
data class LogEntity(
    @PrimaryKey val clientId: String,
    val serverId: Long?,
    val date: String,
    val meal: String,
    val sortKey: Long,
    /** [com.example.foodtracker.model.LoggedFood] as JSON, without id/clientId/pending/sortKey. */
    val payloadJson: String,
    val pending: Boolean,
)

/** Per-day values. [fetchedAt] != null once the day was downloaded from the server. */
@Entity(tableName = "days")
data class DayEntity(
    @PrimaryKey val date: String,
    val hydrationL: Float,
    /** Wire value: "complete" / "incomplete" / null. */
    val status: String?,
    val goalsJson: String?,
    val fetchedAt: Long?,
)

@Entity(tableName = "weights")
data class WeightEntity(
    @PrimaryKey val date: String,
    val weightKg: Float,
    val pending: Boolean,
)

/** Queued [com.example.foodtracker.sync.SyncOp]; [date] is captured at enqueue time. */
@Entity(tableName = "ops")
data class OpEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val date: String,
    val json: String,
    val inFlight: Boolean = false,
    val attempted: Boolean = false,
)

@Entity(tableName = "quick_picks")
data class QuickPickEntity(
    @PrimaryKey val key: String,
    val json: String,
)
