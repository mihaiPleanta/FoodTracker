package com.example.foodtracker.data.cache

import com.example.foodtracker.model.FoodItem

/** Produs cache-uit + momentul scrierii (pentru TTL în repo). */
data class CachedProduct(val item: FoodItem, val cachedAt: Long)

/** Rezultat de search cache-uit, cu produsele deja rezolvate. */
data class CachedSearch(val items: List<FoodItem>, val cachedAt: Long)

/** Abstracție peste cache; Room o implementează, testele folosesc un fake. */
interface FoodCache {
    suspend fun getSearch(query: String): CachedSearch?
    suspend fun putSearch(query: String, items: List<FoodItem>, now: Long)
    suspend fun touchSearch(query: String, barcodes: List<String>, now: Long)

    suspend fun getProduct(barcode: String): CachedProduct?
    suspend fun putProduct(item: FoodItem, now: Long)
    suspend fun touchProduct(barcode: String, now: Long)
}

private const val MAX_PRODUCTS = 200
private const val MAX_SEARCHES = 100

private fun CachedProductEntity.toItem() = FoodItem(
    barcode = barcode,
    name = name,
    brand = brand,
    imageUrl = imageUrl,
    categories = categories,
    per100g = kcal100g.toInt(),
    protein100g = protein100g,
    carbs100g = carbs100g,
    fat100g = fat100g,
)

private fun FoodItem.toEntity(now: Long) = CachedProductEntity(
    barcode = barcode,
    name = name,
    brand = brand,
    imageUrl = imageUrl,
    kcal100g = per100g.toFloat(),
    protein100g = protein100g,
    carbs100g = carbs100g,
    fat100g = fat100g,
    categories = categories,
    cachedAt = now,
    lastAccess = now,
)

class RoomFoodCache(private val dao: FoodCacheDao) : FoodCache {

    override suspend fun getSearch(query: String): CachedSearch? {
        val row = dao.getSearch(query) ?: return null
        if (row.barcodes.isEmpty()) return CachedSearch(emptyList(), row.cachedAt)
        val products = dao.getProducts(row.barcodes).associateBy { it.barcode }
        // Vreun produs evictat → miss (re-fetch), fără rezultate parțiale.
        if (products.size < row.barcodes.size) return null
        val ordered = row.barcodes.mapNotNull { products[it]?.toItem() }
        if (ordered.size < row.barcodes.size) return null
        return CachedSearch(ordered, row.cachedAt)
    }

    override suspend fun putSearch(query: String, items: List<FoodItem>, now: Long) {
        dao.upsertProducts(items.map { it.toEntity(now) })
        dao.upsertSearch(
            CachedSearchEntity(
                query = query,
                barcodes = items.map { it.barcode },
                cachedAt = now,
                lastAccess = now,
            )
        )
        dao.evictProducts(MAX_PRODUCTS)
        dao.evictSearches(MAX_SEARCHES)
    }

    override suspend fun touchSearch(query: String, barcodes: List<String>, now: Long) {
        dao.touchSearch(query, now)
        if (barcodes.isNotEmpty()) dao.touchProducts(barcodes, now)
    }

    override suspend fun getProduct(barcode: String): CachedProduct? =
        dao.getProduct(barcode)?.let { CachedProduct(it.toItem(), it.cachedAt) }

    override suspend fun putProduct(item: FoodItem, now: Long) {
        dao.upsertProducts(listOf(item.toEntity(now)))
        dao.evictProducts(MAX_PRODUCTS)
    }

    override suspend fun touchProduct(barcode: String, now: Long) =
        dao.touchProducts(listOf(barcode), now)
}
