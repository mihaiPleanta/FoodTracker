package com.example.foodtracker.data.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [CachedProductEntity::class, CachedSearchEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(ListStringConverter::class)
abstract class FoodCacheDb : RoomDatabase() {
    abstract fun foodCacheDao(): FoodCacheDao

    companion object {
        @Volatile private var INSTANCE: FoodCacheDb? = null

        fun get(context: Context): FoodCacheDb =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FoodCacheDb::class.java,
                    "food_cache.db",
                ).build().also { INSTANCE = it }
            }
    }
}
