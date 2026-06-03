package com.example.foodtracker.data.cache

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_products")
data class CachedProductEntity(
    @PrimaryKey val barcode: String,
    val name: String,
    val brand: String?,
    @ColumnInfo(name = "image_url") val imageUrl: String?,
    @ColumnInfo(name = "kcal_100g") val kcal100g: Float,
    @ColumnInfo(name = "protein_100g") val protein100g: Float,
    @ColumnInfo(name = "carbs_100g") val carbs100g: Float,
    @ColumnInfo(name = "fat_100g") val fat100g: Float,
    val categories: List<String>,
    @ColumnInfo(name = "cached_at") val cachedAt: Long,
    @ColumnInfo(name = "last_access") val lastAccess: Long,
)
