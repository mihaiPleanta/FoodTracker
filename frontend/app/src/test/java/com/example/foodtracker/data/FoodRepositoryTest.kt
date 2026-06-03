package com.example.foodtracker.data

import com.example.foodtracker.data.cache.CachedProduct
import com.example.foodtracker.data.cache.CachedSearch
import com.example.foodtracker.data.cache.FoodCache
import com.example.foodtracker.api.FoodApi
import com.example.foodtracker.model.FoodItem
import com.example.foodtracker.model.FoodItemDto
import com.example.foodtracker.model.SearchResponseDto
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

private fun item(barcode: String, name: String = "x") = FoodItem(
    barcode = barcode, name = name, per100g = 100,
    protein100g = 1f, carbs100g = 2f, fat100g = 3f,
)

private fun dto(barcode: String, name: String = "x") = FoodItemDto(
    barcode = barcode, name = name, kcal100g = 100f,
    protein100g = 1f, carbs100g = 2f, fat100g = 3f,
)

private fun http(code: Int) =
    HttpException(Response.error<Any>(code, "".toResponseBody(null)))

/** Fake cache in-memory; numără apelurile relevante. */
private class FakeCache : FoodCache {
    val searches = mutableMapOf<String, CachedSearch>()
    val products = mutableMapOf<String, CachedProduct>()
    var touchedSearch = 0
    var touchedProduct = 0

    override suspend fun getSearch(query: String) = searches[query]
    override suspend fun putSearch(query: String, items: List<FoodItem>, now: Long) {
        searches[query] = CachedSearch(items, now)
    }
    override suspend fun touchSearch(query: String, barcodes: List<String>, now: Long) { touchedSearch++ }
    override suspend fun getProduct(barcode: String) = products[barcode]
    override suspend fun putProduct(item: FoodItem, now: Long) {
        products[item.barcode] = CachedProduct(item, now)
    }
    override suspend fun touchProduct(barcode: String, now: Long) { touchedProduct++ }
}

/** Fake FoodApi: cozi de răspunsuri/erori. */
private class FakeApi(
    var searchResult: (() -> SearchResponseDto)? = null,
    var barcodeResult: (() -> FoodItemDto)? = null,
) : FoodApi {
    var searchCalls = 0
    var barcodeCalls = 0
    override suspend fun searchFoods(query: String, pageSize: Int): SearchResponseDto {
        searchCalls++
        return searchResult!!.invoke()
    }
    override suspend fun getFoodByBarcode(barcode: String): FoodItemDto {
        barcodeCalls++
        return barcodeResult!!.invoke()
    }
}

private const val TTL = 24L * 60 * 60 * 1000

class FoodRepositoryTest {

    @Test fun search_freshCacheHit_noNetwork() = runTest {
        val cache = FakeCache().apply {
            searches["banana"] = CachedSearch(listOf(item("1")), cachedAt = 1_000)
        }
        val api = FakeApi(searchResult = { fail("nu trebuie apelat"); error("") })
        val repo = FoodRepository(api, cache, now = { 1_000 + TTL - 1 })

        val result = repo.search("Banana")

        assertEquals(listOf(item("1")), result.items)
        assertTrue(!result.stale)
        assertEquals(0, api.searchCalls)
        assertEquals(1, cache.touchedSearch)
    }

    @Test fun search_miss_fetchesAndCaches() = runTest {
        val cache = FakeCache()
        val api = FakeApi(searchResult = { SearchResponseDto(listOf(dto("1")), 1) })
        val repo = FoodRepository(api, cache, now = { 5_000 })

        val result = repo.search("banana")

        assertEquals("1", result.items.single().barcode)
        assertTrue(!result.stale)
        assertEquals(1, api.searchCalls)
        assertEquals(5_000, cache.searches["banana"]!!.cachedAt)
    }

    @Test fun search_expired_online_refetches() = runTest {
        val cache = FakeCache().apply {
            searches["banana"] = CachedSearch(listOf(item("old")), cachedAt = 0)
        }
        val api = FakeApi(searchResult = { SearchResponseDto(listOf(dto("new")), 1) })
        val repo = FoodRepository(api, cache, now = { TTL + 10 })

        val result = repo.search("banana")

        assertEquals("new", result.items.single().barcode)
        assertEquals(1, api.searchCalls)
    }

    @Test fun search_expired_offline_fallsBackStale() = runTest {
        val cache = FakeCache().apply {
            searches["banana"] = CachedSearch(listOf(item("old")), cachedAt = 0)
        }
        val api = FakeApi(searchResult = { throw IOException("offline") })
        val repo = FoodRepository(api, cache, now = { TTL + 10 })

        val result = repo.search("banana")

        assertEquals("old", result.items.single().barcode)
        assertTrue(result.stale)
    }

    @Test fun search_miss_offline_noCache_rethrows() = runTest {
        val cache = FakeCache()
        val api = FakeApi(searchResult = { throw IOException("offline") })
        val repo = FoodRepository(api, cache, now = { 0 })

        try {
            repo.search("banana"); fail("trebuia să arunce")
        } catch (e: IOException) { /* ok */ }
    }

    @Test fun search_expired_503_fallsBackStale() = runTest {
        val cache = FakeCache().apply {
            searches["banana"] = CachedSearch(listOf(item("old")), cachedAt = 0)
        }
        val api = FakeApi(searchResult = { throw http(503) })
        val repo = FoodRepository(api, cache, now = { TTL + 10 })

        val result = repo.search("banana")
        assertTrue(result.stale)
        assertEquals("old", result.items.single().barcode)
    }

    @Test fun search_normalizesQueryKey() = runTest {
        val cache = FakeCache().apply {
            searches["banana"] = CachedSearch(listOf(item("1")), cachedAt = 1_000)
        }
        val api = FakeApi(searchResult = { fail("nu trebuie apelat"); error("") })
        val repo = FoodRepository(api, cache, now = { 1_000 })

        val result = repo.search("  BaNaNa  ")
        assertEquals(0, api.searchCalls)
        assertEquals("1", result.items.single().barcode)
    }

    @Test fun barcode_freshCacheHit_noNetwork() = runTest {
        val cache = FakeCache().apply {
            products["1"] = CachedProduct(item("1"), cachedAt = 1_000)
        }
        val api = FakeApi(barcodeResult = { fail("nu trebuie apelat"); error("") })
        val repo = FoodRepository(api, cache, now = { 1_000 + TTL - 1 })

        val result = repo.lookupBarcode("1")
        assertEquals("1", result.items.single().barcode)
        assertTrue(!result.stale)
        assertEquals(0, api.barcodeCalls)
        assertEquals(1, cache.touchedProduct)
    }

    @Test fun barcode_miss_fetchesAndCaches() = runTest {
        val cache = FakeCache()
        val api = FakeApi(barcodeResult = { dto("1") })
        val repo = FoodRepository(api, cache, now = { 7_000 })

        val result = repo.lookupBarcode("1")
        assertEquals("1", result.items.single().barcode)
        assertEquals(1, api.barcodeCalls)
        assertEquals(7_000, cache.products["1"]!!.cachedAt)
    }

    @Test fun barcode_404_rethrows() = runTest {
        val cache = FakeCache()
        val api = FakeApi(barcodeResult = { throw http(404) })
        val repo = FoodRepository(api, cache, now = { 0 })

        try {
            repo.lookupBarcode("1"); fail("trebuia să arunce")
        } catch (e: HttpException) { assertEquals(404, e.code()) }
    }

    @Test fun barcode_expired_offline_fallsBackStale() = runTest {
        val cache = FakeCache().apply {
            products["1"] = CachedProduct(item("1", "vechi"), cachedAt = 0)
        }
        val api = FakeApi(barcodeResult = { throw IOException("offline") })
        val repo = FoodRepository(api, cache, now = { TTL + 10 })

        val result = repo.lookupBarcode("1")
        assertTrue(result.stale)
        assertEquals("vechi", result.items.single().name)
    }
}
