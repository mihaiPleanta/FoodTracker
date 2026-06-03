package com.example.foodtracker.data.cache

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_searches")
data class CachedSearchEntity(
    @PrimaryKey val query: String,
    val barcodes: List<String>,
    @ColumnInfo(name = "cached_at") val cachedAt: Long,
    @ColumnInfo(name = "last_access") val lastAccess: Long,
)
