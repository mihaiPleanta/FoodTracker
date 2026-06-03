package com.example.foodtracker.data

import com.example.foodtracker.api.FoodApi
import com.example.foodtracker.data.cache.FoodCache
import com.example.foodtracker.model.FoodItem
import retrofit2.HttpException
import java.io.IOException

/** Rezultat servit din repo; [stale] = servit din cache ca fallback offline. */
data class SearchResult(val items: List<FoodItem>, val stale: Boolean)

private const val TTL_MILLIS = 24L * 60 * 60 * 1000

class FoodRepository(
    private val api: FoodApi,
    private val cache: FoodCache,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private fun fresh(cachedAt: Long) = now() - cachedAt < TTL_MILLIS

    suspend fun search(rawQuery: String): SearchResult {
        val query = rawQuery.trim().lowercase()
        val cached = cache.getSearch(query)
        if (cached != null && fresh(cached.cachedAt)) {
            cache.touchSearch(query, now())
            return SearchResult(cached.items, stale = false)
        }
        return try {
            val items = api.searchFoods(query).items.map { it.toDomain() }
            cache.putSearch(query, items, now())
            SearchResult(items, stale = false)
        } catch (e: IOException) {
            cached?.let { SearchResult(it.items, stale = true) } ?: throw e
        } catch (e: HttpException) {
            if (e.code() == 503 && cached != null) SearchResult(cached.items, stale = true)
            else throw e
        }
    }

    suspend fun lookupBarcode(barcode: String): SearchResult {
        val cached = cache.getProduct(barcode)
        if (cached != null && fresh(cached.cachedAt)) {
            cache.touchProduct(barcode, now())
            return SearchResult(listOf(cached.item), stale = false)
        }
        return try {
            val item = api.getFoodByBarcode(barcode).toDomain()
            cache.putProduct(item, now())
            SearchResult(listOf(item), stale = false)
        } catch (e: IOException) {
            cached?.let { SearchResult(listOf(it.item), stale = true) } ?: throw e
        } catch (e: HttpException) {
            if (e.code() == 503 && cached != null) SearchResult(listOf(cached.item), stale = true)
            else throw e
        }
    }
}
