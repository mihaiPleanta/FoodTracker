package com.example.foodtracker

import com.example.foodtracker.api.QuickPicksApi
import com.example.foodtracker.model.FoodItem
import com.example.foodtracker.model.FoodItemDto
import com.example.foodtracker.model.RecentFoodDto
import com.example.foodtracker.model.toDto
import com.example.foodtracker.viewmodel.QuickListState
import com.example.foodtracker.viewmodel.QuickPicksViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

private fun food(code: String) = FoodItem(
    barcode = code, name = "Food $code", per100g = 100, protein100g = 1f, carbs100g = 1f, fat100g = 1f,
)

private class FakeQuickPicksApi : QuickPicksApi {
    var recent: List<RecentFoodDto> = emptyList()
    var favorites: List<FoodItemDto> = emptyList()
    var failRecent = false
    var failFavorites = false
    var failWrites = false
    val requestedMeals = mutableListOf<String>()
    val added = mutableListOf<String>()
    val removed = mutableListOf<String>()
    var favoritesGate: CompletableDeferred<Unit>? = null
    val recentGates = mutableMapOf<String, CompletableDeferred<Unit>>()
    val recentByMeal = mutableMapOf<String, List<RecentFoodDto>>()
    val failBarcodes = mutableSetOf<String>()

    override suspend fun getRecent(meal: String): List<RecentFoodDto> {
        requestedMeals += meal
        recentGates[meal]?.await()
        if (failRecent) throw IOException("offline")
        return recentByMeal[meal] ?: recent
    }
    override suspend fun getFavorites(): List<FoodItemDto> {
        favoritesGate?.await()
        if (failFavorites) throw IOException("offline")
        return favorites
    }
    override suspend fun addFavorite(barcode: String, food: FoodItemDto): FoodItemDto {
        if (failWrites || barcode in failBarcodes) throw IOException("offline")
        added += barcode
        return food
    }
    override suspend fun removeFavorite(barcode: String): Response<Unit> {
        if (failWrites) throw IOException("offline")
        removed += barcode
        return Response.success(null)
    }
}

/** Reads favorites synchronously (favoriteBarcodes is derived via stateIn and lags until the dispatcher runs). */
private fun favCodes(vm: QuickPicksViewModel): Set<String> =
    (vm.favorites.value as QuickListState.Loaded).items.map { it.barcode }.toSet()

@OptIn(ExperimentalCoroutinesApi::class)
class QuickPicksViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun load_fetchesBothListsWithBackendMealKey() = runTest(testDispatcher) {
        val api = FakeQuickPicksApi().apply {
            recent = listOf(RecentFoodDto(food("a").toDto(), 150))
            favorites = listOf(food("f").toDto())
        }
        val vm = QuickPicksViewModel(api)
        vm.load("Breakfast")
        advanceUntilIdle()

        assertEquals(listOf("BREAKFAST"), api.requestedMeals)
        val recent = vm.recent.value as QuickListState.Loaded
        assertEquals(150, recent.items.single().lastGrams)
        assertEquals(setOf("f"), vm.favoriteBarcodes.value)
    }

    @Test
    fun load_withEmptyLists_isLoadedEmpty() = runTest(testDispatcher) {
        val vm = QuickPicksViewModel(FakeQuickPicksApi())
        vm.load("Lunch")
        advanceUntilIdle()
        assertEquals(QuickListState.Loaded(emptyList<Any>()), vm.recent.value)
        assertEquals(QuickListState.Loaded(emptyList<Any>()), vm.favorites.value)
    }

    @Test
    fun load_setsLoadingBeforeFetching() = runTest(testDispatcher) {
        val api = FakeQuickPicksApi().apply { favorites = listOf(food("old").toDto()) }
        val vm = QuickPicksViewModel(api)
        vm.load("Lunch")
        advanceUntilIdle()
        api.favorites = listOf(food("new").toDto())

        vm.load("Lunch")
        assertEquals(QuickListState.Loading, vm.favorites.value)   // no stale list shown
        advanceUntilIdle()
        assertEquals(setOf("new"), vm.favoriteBarcodes.value)
    }

    @Test
    fun failureInOneList_doesNotBreakTheOther() = runTest(testDispatcher) {
        val api = FakeQuickPicksApi().apply {
            failRecent = true
            favorites = listOf(food("f").toDto())
        }
        val vm = QuickPicksViewModel(api)
        vm.load("Dinner")
        advanceUntilIdle()
        assertEquals(QuickListState.Error(R.string.error_no_internet), vm.recent.value)
        assertTrue(vm.favorites.value is QuickListState.Loaded)
    }

    @Test
    fun refreshRecent_reusesLastMeal() = runTest(testDispatcher) {
        val api = FakeQuickPicksApi()
        val vm = QuickPicksViewModel(api)
        vm.load("Snacks")
        advanceUntilIdle()
        vm.refreshRecent()
        advanceUntilIdle()
        assertEquals(listOf("SNACKS", "SNACKS"), api.requestedMeals)
    }

    @Test
    fun refreshRecent_beforeLoad_isNoOp() = runTest(testDispatcher) {
        val api = FakeQuickPicksApi()
        val vm = QuickPicksViewModel(api)
        vm.refreshRecent()
        advanceUntilIdle()
        assertTrue(api.requestedMeals.isEmpty())
    }

    @Test
    fun toggleFavorite_addsOptimisticallyThenPuts() = runTest(testDispatcher) {
        val api = FakeQuickPicksApi()
        val vm = QuickPicksViewModel(api)
        vm.load("Lunch")
        advanceUntilIdle()

        vm.toggleFavorite(food("x"))
        assertEquals(setOf("x"), favCodes(vm))                        // before network
        advanceUntilIdle()
        assertEquals(listOf("x"), api.added)
    }

    @Test
    fun toggleFavorite_removesExisting() = runTest(testDispatcher) {
        val api = FakeQuickPicksApi().apply { favorites = listOf(food("x").toDto()) }
        val vm = QuickPicksViewModel(api)
        vm.load("Lunch")
        advanceUntilIdle()

        vm.toggleFavorite(food("x"))
        assertTrue(favCodes(vm).isEmpty())                            // before network
        advanceUntilIdle()
        assertEquals(listOf("x"), api.removed)
    }

    @Test
    fun toggleFavorite_rollsBackAndToastsOnFailure() = runTest(testDispatcher) {
        val api = FakeQuickPicksApi().apply { failWrites = true }
        val vm = QuickPicksViewModel(api)
        val toasts = mutableListOf<Int>()
        backgroundScope.launch { vm.toastEvents.collect { toasts += it } }
        vm.load("Lunch")
        advanceUntilIdle()

        vm.toggleFavorite(food("x"))
        advanceUntilIdle()
        runCurrent()   // advanceUntilIdle ignores backgroundScope tasks (the toast collector's resume)

        assertTrue(vm.favoriteBarcodes.value.isEmpty())
        assertEquals(listOf(R.string.error_favorite_update), toasts)
    }

    @Test
    fun resetUserState_clearsLists() = runTest(testDispatcher) {
        val api = FakeQuickPicksApi().apply { favorites = listOf(food("x").toDto()) }
        val vm = QuickPicksViewModel(api)
        vm.load("Lunch")
        advanceUntilIdle()

        vm.resetUserState()
        assertEquals(QuickListState.Loading, vm.favorites.value)
        assertEquals(QuickListState.Loading, vm.recent.value)
        advanceUntilIdle()
        assertTrue(vm.favoriteBarcodes.value.isEmpty())
        vm.refreshRecent(); advanceUntilIdle()
        assertEquals(listOf("LUNCH"), api.requestedMeals)          // last meal forgotten
    }

    @Test
    fun resetUserState_whileFetchesInFlight_neverResurrectsOldData() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        val api = FakeQuickPicksApi().apply {
            favorites = listOf(food("old").toDto())
            recent = listOf(RecentFoodDto(food("oldr").toDto(), 10))
            favoritesGate = gate
            recentGates["LUNCH"] = gate
        }
        val vm = QuickPicksViewModel(api)
        vm.load("Lunch")
        advanceUntilIdle()
        vm.resetUserState()
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(QuickListState.Loading, vm.favorites.value)
        assertEquals(QuickListState.Loading, vm.recent.value)
    }

    @Test
    fun failedToggle_rollsBackOnlyThatBarcode() = runTest(testDispatcher) {
        val api = FakeQuickPicksApi().apply { failBarcodes += "a" }
        val vm = QuickPicksViewModel(api)
        vm.load("Lunch")
        advanceUntilIdle()
        vm.toggleFavorite(food("a"))
        vm.toggleFavorite(food("b"))
        advanceUntilIdle()
        assertEquals(setOf("b"), favCodes(vm))
    }

    @Test
    fun load_ignoresStaleResponseFromPreviousMeal() = runTest(testDispatcher) {
        val lunchGate = CompletableDeferred<Unit>()
        val api = FakeQuickPicksApi().apply {
            recentGates["LUNCH"] = lunchGate
            recentByMeal["LUNCH"] = listOf(RecentFoodDto(food("lunch").toDto(), 1))
            recentByMeal["DINNER"] = listOf(RecentFoodDto(food("dinner").toDto(), 2))
        }
        val vm = QuickPicksViewModel(api)
        vm.load("Lunch")
        advanceUntilIdle()
        vm.load("Dinner")
        advanceUntilIdle()
        lunchGate.complete(Unit)
        advanceUntilIdle()
        val items = (vm.recent.value as QuickListState.Loaded).items
        assertEquals("dinner", items.single().food.barcode)
    }

    @Test
    fun recordAdded_movesFoodFirstWithGramsAndDedups() = runTest(testDispatcher) {
        val api = FakeQuickPicksApi().apply {
            recent = listOf(RecentFoodDto(food("a").toDto(), 100), RecentFoodDto(food("b").toDto(), 50))
        }
        val vm = QuickPicksViewModel(api)
        vm.load("Breakfast")
        advanceUntilIdle()
        vm.recordAdded(food("b"), 230)
        val items = (vm.recent.value as QuickListState.Loaded).items
        assertEquals(listOf("b", "a"), items.map { it.food.barcode })
        assertEquals(230, items.first().lastGrams)
    }

    @Test
    fun recordAdded_whileLoading_staysLoading() = runTest(testDispatcher) {
        val vm = QuickPicksViewModel(FakeQuickPicksApi())
        vm.recordAdded(food("a"), 100)
        assertEquals(QuickListState.Loading, vm.recent.value)
    }

    @Test
    fun recordAdded_capsListAt20() = runTest(testDispatcher) {
        val api = FakeQuickPicksApi().apply {
            recent = (1..20).map { RecentFoodDto(food("f$it").toDto(), 100) }
        }
        val vm = QuickPicksViewModel(api)
        vm.load("Breakfast")
        advanceUntilIdle()
        vm.recordAdded(food("new"), 10)
        val items = (vm.recent.value as QuickListState.Loaded).items
        assertEquals(20, items.size)
        assertEquals("new", items.first().food.barcode)
        assertTrue(items.none { it.food.barcode == "f20" })
    }
}
