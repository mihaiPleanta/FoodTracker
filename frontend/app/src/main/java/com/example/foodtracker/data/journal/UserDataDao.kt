package com.example.foodtracker.data.journal

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Runs [block] atomically (Room: `db.withTransaction`). A plain interface: Kotlin `fun interface`
 *  cannot declare a generic abstract method. */
interface TxRunner {
    suspend fun <R> run(block: suspend () -> R): R
}

@Dao
interface UserDataDao {
    // ── Observed ──
    @Query("SELECT * FROM logs WHERE date = :date ORDER BY sortKey, serverId")
    fun observeLogs(date: String): Flow<List<LogEntity>>

    @Query("SELECT * FROM days WHERE date = :date")
    fun observeDay(date: String): Flow<DayEntity?>

    @Query("SELECT * FROM weights WHERE date = :date")
    fun observeWeight(date: String): Flow<WeightEntity?>

    @Query("SELECT * FROM weights ORDER BY date")
    fun observeWeights(): Flow<List<WeightEntity>>

    @Query("SELECT COUNT(*) FROM ops")
    fun observeOpCount(): Flow<Int>

    // ── Logs ──
    @Query("SELECT * FROM logs WHERE date = :date ORDER BY sortKey, serverId")
    suspend fun getLogs(date: String): List<LogEntity>

    @Query("SELECT * FROM logs WHERE clientId = :clientId")
    suspend fun getLog(clientId: String): LogEntity?

    @Query("SELECT * FROM logs WHERE date BETWEEN :from AND :to ORDER BY date, sortKey, serverId")
    suspend fun getLogsBetween(from: String, to: String): List<LogEntity>

    @Query("SELECT COUNT(*) FROM logs WHERE date = :date")
    suspend fun countLogs(date: String): Int

    @Upsert
    suspend fun upsertLogs(logs: List<LogEntity>)

    @Query("DELETE FROM logs WHERE clientId IN (:clientIds)")
    suspend fun deleteLogs(clientIds: List<String>)

    // ── Days ──
    @Query("SELECT * FROM days WHERE date = :date")
    suspend fun getDay(date: String): DayEntity?

    @Query("SELECT * FROM days WHERE date BETWEEN :from AND :to ORDER BY date")
    suspend fun getDays(from: String, to: String): List<DayEntity>

    @Upsert
    suspend fun upsertDays(days: List<DayEntity>)

    // ── Weights ──
    @Query("SELECT * FROM weights WHERE date = :date")
    suspend fun getWeight(date: String): WeightEntity?

    @Query("SELECT * FROM weights WHERE date BETWEEN :from AND :to ORDER BY date")
    suspend fun getWeightsBetween(from: String, to: String): List<WeightEntity>

    @Upsert
    suspend fun upsertWeights(weights: List<WeightEntity>)

    @Query("DELETE FROM weights WHERE date IN (:dates)")
    suspend fun deleteWeights(dates: List<String>)

    // ── Op queue ──
    @Query("SELECT * FROM ops ORDER BY seq")
    suspend fun getOps(): List<OpEntity>

    @Query("SELECT * FROM ops ORDER BY seq LIMIT 1")
    suspend fun firstOp(): OpEntity?

    @Query("SELECT * FROM ops WHERE seq = :seq")
    suspend fun getOp(seq: Long): OpEntity?

    @Insert
    suspend fun insertOp(op: OpEntity): Long

    @Update
    suspend fun updateOp(op: OpEntity)

    @Query("DELETE FROM ops WHERE seq IN (:seqs)")
    suspend fun deleteOps(seqs: List<Long>)

    @Query("UPDATE ops SET inFlight = 1, attempted = 1 WHERE seq = :seq")
    suspend fun markInFlight(seq: Long)

    @Query("UPDATE ops SET inFlight = 0")
    suspend fun releaseInFlight()

    // ── Quick picks ──
    @Query("SELECT * FROM quick_picks WHERE `key` = :key")
    suspend fun getQuickPick(key: String): QuickPickEntity?

    @Upsert
    suspend fun upsertQuickPick(entity: QuickPickEntity)

    // ── Clear ──
    @Query("DELETE FROM logs") suspend fun clearLogs()
    @Query("DELETE FROM days") suspend fun clearDays()
    @Query("DELETE FROM weights") suspend fun clearWeights()
    @Query("DELETE FROM ops") suspend fun clearOps()
    @Query("DELETE FROM quick_picks") suspend fun clearQuickPicks()
}
