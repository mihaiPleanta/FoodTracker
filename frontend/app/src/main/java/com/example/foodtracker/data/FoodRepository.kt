package com.example.foodtracker.data

import com.example.foodtracker.api.FoodApi
import com.example.foodtracker.data.cache.FoodCache
import com.example.foodtracker.model.FoodItem
import retrofit2.HttpException
import java.io.IOException

/** Rezultat servit din repo; [stale] = servit din cache ca fallback offline. */
data class SearchResult(val items: List<FoodItem>, val stale: Boolean, val hasMore: Boolean = false)

private const val TTL_MILLIS = 24L * 60 * 60 * 1000

class FoodRepository(
    private val api: FoodApi,
    private val cache: FoodCache,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private fun fresh(cachedAt: Long) = now() - cachedAt < TTL_MILLIS

    suspend fun search(rawQuery: String, page: Int = 1): SearchResult {
        val query = rawQuery.trim().lowercase()

        // Paginile 2+ sunt network-only: fără cache (read sau write), fără fallback offline.
        if (page > 1) {
            val resp = api.searchFoods(query, page)
            return SearchResult(resp.items.map { it.toDomain() }, stale = false, hasMore = resp.hasMore)
        }

        val cached = cache.getSearch(query)
        if (cached != null && fresh(cached.cachedAt)) {
            cache.touchSearch(query, cached.items.map { it.barcode }, now())
            return SearchResult(cached.items, stale = false, hasMore = false)
        }
        return try {
            val resp = api.searchFoods(query, page)
            val items = resp.items.map { it.toDomain() }
            cache.putSearch(query, items, now())
            SearchResult(items, stale = false, hasMore = resp.hasMore)
        } catch (e: IOException) {
            cached?.let { SearchResult(it.items, stale = true, hasMore = false) } ?: throw e
        } catch (e: HttpException) {
            if (e.code() == 503 && cached != null) SearchResult(cached.items, stale = true, hasMore = false)
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
