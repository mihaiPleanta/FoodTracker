package com.example.foodtracker.data.journal

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction

/** User data mirror (logs, days, weights) + pending op queue. Wiped on logout. */
@Database(
    entities = [LogEntity::class, DayEntity::class, WeightEntity::class, OpEntity::class, QuickPickEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class UserDataDb : RoomDatabase() {
    abstract fun userDataDao(): UserDataDao

    val txRunner: TxRunner = object : TxRunner {
        override suspend fun <R> run(block: suspend () -> R): R = withTransaction { block() }
    }

    companion object {
        @Volatile private var INSTANCE: UserDataDb? = null

        fun get(context: Context): UserDataDb =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    UserDataDb::class.java,
                    "user_data.db",
                ).build().also { INSTANCE = it }
            }
    }
}
