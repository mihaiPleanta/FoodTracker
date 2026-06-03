package com.example.foodtracker.data.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FoodCacheDao {
    // ── Produse ──
    @Query("SELECT * FROM cached_products WHERE barcode = :barcode LIMIT 1")
    suspend fun getProduct(barcode: String): CachedProductEntity?

    @Query("SELECT * FROM cached_products WHERE barcode IN (:barcodes)")
    suspend fun getProducts(barcodes: List<String>): List<CachedProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProducts(products: List<CachedProductEntity>)

    @Query("UPDATE cached_products SET last_access = :now WHERE barcode IN (:barcodes)")
    suspend fun touchProducts(barcodes: List<String>, now: Long)

    @Query(
        "DELETE FROM cached_products WHERE barcode NOT IN " +
            "(SELECT barcode FROM cached_products ORDER BY last_access DESC LIMIT :cap)"
    )
    suspend fun evictProducts(cap: Int)

    // ── Search-uri ──
    @Query("SELECT * FROM cached_searches WHERE `query` = :query LIMIT 1")
    suspend fun getSearch(query: String): CachedSearchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSearch(search: CachedSearchEntity)

    @Query("UPDATE cached_searches SET last_access = :now WHERE `query` = :query")
    suspend fun touchSearch(query: String, now: Long)

    @Query(
        "DELETE FROM cached_searches WHERE `query` NOT IN " +
            "(SELECT `query` FROM cached_searches ORDER BY last_access DESC LIMIT :cap)"
    )
    suspend fun evictSearches(cap: Int)
}
