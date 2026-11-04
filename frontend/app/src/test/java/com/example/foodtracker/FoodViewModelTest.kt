package com.example.foodtracker

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.foodtracker.api.FoodApi
import com.example.foodtracker.api.LogsApi
import com.example.foodtracker.api.ProfileApi
import com.example.foodtracker.data.FakeSettingsRepository
import com.example.foodtracker.data.SettingsRepository
import com.example.foodtracker.model.MealReminder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import com.example.foodtracker.data.FoodRepository
import com.example.foodtracker.data.MealLogTracker
import com.example.foodtracker.data.cache.CachedProduct
import com.example.foodtracker.data.cache.CachedSearch
import com.example.foodtracker.data.cache.FoodCache
import com.example.foodtracker.model.*
import com.example.foodtracker.model.FoodItem
import com.example.foodtracker.model.FoodItemDto
import com.example.foodtracker.model.LoggedFood
import com.example.foodtracker.model.SearchResponseDto
import com.example.foodtracker.viewmodel.FoodViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import com.example.foodtracker.sync.SyncOp
import com.example.foodtracker.sync.SyncOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

// No-op stubs used only to satisfy FoodViewModel's FoodRepository dependency.
private object NoOpCache : FoodCache {
    override suspend fun getSearch(query: String): CachedSearch? = null
    override suspend fun putSearch(query: String, items: List<FoodItem>, now: Long) {}
    override suspend fun touchSearch(query: String, barcodes: List<String>, now: Long) {}
    override suspend fun getProduct(barcode: String): CachedProduct? = null
    override suspend fun putProduct(item: FoodItem, now: Long) {}
    override suspend fun touchProduct(barcode: String, now: Long) {}
    override suspend fun clearSearches() {}
    override suspend fun deleteProducts(barcodes: List<String>) {}
    override suspend fun clearAll() {}
}

private object NoOpFoodApi : FoodApi {
    override suspend fun searchFoods(query: String, page: Int, pageSize: Int): SearchResponseDto =
        SearchResponseDto(emptyList(), 0)
    override suspend fun getFoodByBarcode(barcode: String): FoodItemDto =
        throw UnsupportedOperationException("NoOpFoodApi")
}


/**
 * Keeps FoodViewModel's init loadGoals() off the network: the real ProfileApi goes through
 * AuthInterceptor -> FirebaseAuth on an OkHttp thread and leaks an uncaught exception
 * into the next test (UncaughtExceptionsBeforeTest).
 */
private object NoOpProfileApi : ProfileApi {
    override suspend fun getProfile(): retrofit2.Response<ProfileDto> = throw UnsupportedOperationException()
    override suspend fun saveProfile(profile: ProfileDto): retrofit2.Response<ProfileDto> =
        throw UnsupportedOperationException()
    override suspend fun calculateTdee(profile: ProfileDto): retrofit2.Response<TdeeResponseDto> =
        throw UnsupportedOperationException()
    override suspend fun getGoals(): retrofit2.Response<GoalsDto> = retrofit2.Response.success(null)
    override suspend fun deleteAccount(): retrofit2.Response<Unit> = throw UnsupportedOperationException()
}

/** ProfileApi that records saveProfile calls, can be made to fail, and serves a configurable GoalsDto. */
private class RecordingProfileApi : ProfileApi {
    val saved = mutableListOf<ProfileDto>()
    var getGoalsCalls = 0
    var saveFailure: Throwable? = null
    var goals: GoalsDto? = null

    override suspend fun getProfile(): retrofit2.Response<ProfileDto> = throw UnsupportedOperationException()
    override suspend fun saveProfile(profile: ProfileDto): retrofit2.Response<ProfileDto> {
        saveFailure?.let { throw it }
        saved += profile
        return retrofit2.Response.success(profile)
    }
    override suspend fun calculateTdee(profile: ProfileDto): retrofit2.Response<TdeeResponseDto> =
        throw UnsupportedOperationException()
    override suspend fun getGoals(): retrofit2.Response<GoalsDto> {
        getGoalsCalls++
        return retrofit2.Response.success(goals)
    }
    override suspend fun deleteAccount(): retrofit2.Response<Unit> = throw UnsupportedOperationException()
}

private fun goalsWithHydration(goalL: Float) = GoalsDto(
    calorieGoal = 2000, proteinGoal = 150, carbsGoal = 250, fatGoal = 65, mode = "MAINTENANCE",
    hydration = HydrationGoalDto(goalL, goalL, false, 2.7f, 0.5f, 0f),
)

private fun pagedDto(barcode: String) = FoodItemDto(
    barcode = barcode, name = "Food $barcode", kcal100g = 100f,
    protein100g = 5f, carbs100g = 10f, fat100g = 2f,
)

/** Returns page 1 with two items + hasMore=true, page 2 with one item + hasMore=false. */
private object PagingFoodApi : FoodApi {
    override suspend fun searchFoods(query: String, page: Int, pageSize: Int): SearchResponseDto =
        if (page <= 1) SearchResponseDto(listOf(pagedDto("1"), pagedDto("2")), 2, hasMore = true)
        else SearchResponseDto(listOf(pagedDto("3")), 1, hasMore = false)
    override suspend fun getFoodByBarcode(barcode: String): FoodItemDto =
        throw UnsupportedOperationException("PagingFoodApi")
}


@OptIn(ExperimentalCoroutinesApi::class)
class FoodViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    // DataStore defaults to Dispatchers.IO; a write finishing after resetMain() would resume a
    // viewModelScope coroutine on a missing Main dispatcher and fail the next test
    // (UncaughtExceptionsBeforeTest). Keep it on the test dispatcher and cancel it in tearDown.
    private val dataStoreScope = CoroutineScope(testDispatcher + SupervisorJob())
    private lateinit var viewModel: FoodViewModel

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = newVm()
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
        Dispatchers.resetMain()
    }

    private fun newTracker() = MealLogTracker(
        PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { File(tempFolder.newFolder(), "meal_log_tracker.preferences_pb") }
        )
    )

    private fun newVm(
        journal: TestJournal = TestJournal(),
        profileApi: ProfileApi = NoOpProfileApi,
        foodApi: FoodApi = NoOpFoodApi,
        settingsRepository: SettingsRepository = FakeSettingsRepository(),
        tracker: MealLogTracker = newTracker(),
    ) = FoodViewModel(
        settingsRepository = settingsRepository,
        mealLogTracker = tracker,
        foodRepository = FoodRepository(foodApi, NoOpCache),
        journal = journal.repo,
        profileApi = profileApi,
    )

    private fun todayKey() = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

    /** A journal whose server has [logs] on today's breakfast. */
    private fun journalWithBreakfast(vararg logs: FoodLogDto) = TestJournal().apply {
        logsApi.days[todayKey()] = DayResponseDto(mapOf("breakfast" to logs.toList()), 0f, null)
    }

    private fun TestScope.collectToasts(vm: FoodViewModel): MutableList<FoodViewModel.ToastEvent> {
        val toasts = mutableListOf<FoodViewModel.ToastEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.toastEvents.collect { toasts += it } }
        return toasts
    }

    private fun TestScope.collectFoods(vm: FoodViewModel, meal: String): () -> List<LoggedFood> {
        var foods: List<LoggedFood> = emptyList()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.getFoodsFlow(meal).collect { foods = it } }
        return { foods }
    }

    private fun TestScope.countEmissions(flow: kotlinx.coroutines.flow.Flow<Unit>): () -> Int {
        var count = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { count++ } }
        return { count }
    }

    @Test
    fun lookupBarcode_setsLastLookupBarcode_andSearchClearsIt() = runTest(testDispatcher) {
        assertEquals(null, viewModel.lastLookupBarcode.value)
        viewModel.lookupBarcode("5941234567890")
        assertEquals("5941234567890", viewModel.lastLookupBarcode.value)
        viewModel.searchFoods("iaurt")
        assertEquals(null, viewModel.lastLookupBarcode.value)

        viewModel.lookupBarcode("12345678")
        assertEquals("12345678", viewModel.lastLookupBarcode.value)
        viewModel.clearSearch()
        assertEquals(null, viewModel.lastLookupBarcode.value)
        advanceUntilIdle()
    }

    @Test
    fun updateUserProfile_updatesValue() {
        val profile = UserProfile(name = "Ana", age = 25, gender = Gender.FEMALE)
        viewModel.updateUserProfile(profile)
        assertEquals(profile, viewModel.userProfile.value)
    }

    @Test
    fun persistedSettings_firstValueIsStoredOneNotDefaults() = runTest(testDispatcher) {
        // Reminder sync must never run on the fabricated AppSettings() placeholder that
        // appSettings starts with before DataStore loads — it would reschedule custom times.
        val stored = AppSettings(lunchReminder = MealReminder(enabled = true, minuteOfDay = 750))
        val slowRepo = object : SettingsRepository {
            override val settings = flow { delay(1_000); emit(stored) }
            override suspend fun update(settings: AppSettings) = Unit
        }
        val vm = newVm(settingsRepository = slowRepo)
        assertEquals(stored, vm.persistedSettings.first())
    }

    @Test
    fun updateAppSettings_propagatesThroughRepoToStateFlow() = runTest(testDispatcher) {
        val settings = AppSettings(notificationsEnabled = false, language = AppLanguage.ENGLISH)
        viewModel.updateAppSettings(settings)
        advanceUntilIdle()    // drains repo.update + StateFlow propagation
        assertEquals(settings, viewModel.appSettings.value)
    }

    private fun sampleFood(name: String) = FoodItem(
        barcode = "1234567890123",
        name = name,
        brand = null,
        imageUrl = null,
        categories = emptyList(),
        per100g = 100,
        protein100g = 5f,
        carbs100g = 10f,
        fat100g = 2f,
    )

    private val bread = FoodItem(
        barcode = "999", name = "Bread", brand = null, imageUrl = null,
        categories = emptyList(), per100g = 250, protein100g = 8f, carbs100g = 45f, fat100g = 3f,
    )

    @Test
    fun addFoodToMealReturningIndex_returnsZeroForFirstAdd() {
        val index = viewModel.addFoodToMealReturningIndex(
            "Breakfast",
            LoggedFood(sampleFood("Oats"), 100)
        )
        assertEquals(0, index)
    }

    @Test
    fun addFoodToMealReturningIndex_returnsIncrementingIndexes() {
        val firstIndex = viewModel.addFoodToMealReturningIndex("Lunch", LoggedFood(sampleFood("Rice"), 200))
        val secondIndex = viewModel.addFoodToMealReturningIndex("Lunch", LoggedFood(sampleFood("Chicken"), 150))
        val thirdIndex = viewModel.addFoodToMealReturningIndex("Lunch", LoggedFood(sampleFood("Broccoli"), 80))
        assertEquals(0, firstIndex)
        assertEquals(1, secondIndex)
        assertEquals(2, thirdIndex)
    }

    @Test
    fun addFoodToMealReturningIndex_countsRowsAlreadyInTheMirror() = runTest(testDispatcher) {
        val vm = newVm(journalWithBreakfast(serverLog(1, todayKey()), serverLog(2, todayKey())))
        advanceUntilIdle()
        assertEquals(2, vm.addFoodToMealReturningIndex("Breakfast", LoggedFood(bread, 30)))
        advanceUntilIdle()
        assertEquals(3, vm.addFoodToMealReturningIndex("Breakfast", LoggedFood(bread, 40)))
    }

    @Test
    fun loadMore_appendsNextPage_andUpdatesHasMore() = runTest(testDispatcher) {
        val vm = newVm(foodApi = PagingFoodApi)

        vm.searchFoods("banana")
        advanceUntilIdle()   // drains the 350ms debounce + page-1 fetch
        val first = vm.searchState.value as FoodViewModel.SearchUiState.Results
        assertEquals(2, first.items.size)
        assertEquals(true, first.hasMore)

        vm.loadMore()
        advanceUntilIdle()
        val after = vm.searchState.value as FoodViewModel.SearchUiState.Results
        assertEquals(3, after.items.size)
        assertEquals(false, after.hasMore)
    }

    // ── Journal: reads ────────────────────────────────────────────────────────

    @Test
    fun homeShowsServerDayThroughTheMirror() = runTest(testDispatcher) {
        val vm = newVm(journalWithBreakfast(serverLog(42, todayKey(), name = "Oats")))
        val foods = collectFoods(vm, "Breakfast")
        advanceUntilIdle()   // init refresh of today
        assertEquals(listOf("Oats"), foods().map { it.food.name })
        assertEquals(42L, foods()[0].id)
        assertFalse(foods()[0].pending)
    }

    @Test
    fun setSelectedHomeDate_alwaysRefreshesFromServer() = runTest(testDispatcher) {
        val journal = journalWithBreakfast(serverLog(1, todayKey()))
        val vm = newVm(journal)
        val foods = collectFoods(vm, "Breakfast")
        advanceUntilIdle()
        assertEquals(1, foods().size)

        // Another device deleted the log; coming back to Home re-fetches the day.
        journal.logsApi.days[todayKey()] = DayResponseDto(emptyMap(), 0f, null)
        vm.setSelectedHomeDate(java.util.Date())
        advanceUntilIdle()

        assertEquals(0, foods().size)
        assertEquals(2, journal.logsApi.dayRequests.count { it == todayKey() })
    }

    @Test
    fun selectingNeverFetchedDayOffline_toastsNoInternet() = runTest(testDispatcher) {
        val journal = TestJournal().apply { logsApi.dayFailure = java.io.IOException("offline") }
        val vm = newVm(journal)
        val toasts = collectToasts(vm)
        advanceUntilIdle()
        assertEquals(listOf(R.string.error_no_internet), toasts.map { it.messageRes })
        assertFalse(vm.loadingDay.value)
    }

    @Test
    fun selectingFetchedDayOffline_isSilentAndShowsTheMirror() = runTest(testDispatcher) {
        val journal = journalWithBreakfast(serverLog(1, todayKey(), name = "Oats"))
        val vm = newVm(journal)
        val toasts = collectToasts(vm)
        val foods = collectFoods(vm, "Breakfast")
        advanceUntilIdle()

        journal.logsApi.dayFailure = java.io.IOException("offline")
        vm.setSelectedHomeDate(java.util.Date())
        advanceUntilIdle()

        assertEquals(emptyList<Int>(), toasts.map { it.messageRes })
        assertEquals(listOf("Oats"), foods().map { it.food.name })
    }

    // ── Journal: writes are local + queued, never rolled back ───────────────────

    @Test
    fun addFoodOfflineStaysVisibleAsPending() = runTest(testDispatcher) {
        val journal = TestJournal().apply { logsApi.dayFailure = java.io.IOException("offline") }
        val vm = newVm(journal)
        val toasts = collectToasts(vm)
        val foods = collectFoods(vm, "Breakfast")
        advanceUntilIdle()
        toasts.clear()   // the never-fetched day's load error, not the add's

        vm.addFoodToMeal("Breakfast", LoggedFood(bread, 60, unit = "slice", unitQty = 2f))
        advanceUntilIdle()

        assertEquals(1, foods().size)
        assertTrue(foods()[0].pending)
        assertNotNull(foods()[0].clientId)
        assertEquals(emptyList<Int>(), toasts.map { it.messageRes })
        val create = journal.ops().single() as SyncOp.CreateLog
        assertEquals(foods()[0].clientId, create.clientId)
        assertEquals(60, create.body.grams)
        assertEquals("slice", create.body.unit)
        assertEquals(2f, create.body.unitQty)
        assertTrue(journal.scheduler.calls > 0)
    }

    @Test
    fun deleteOfflineStaysDeleted() = runTest(testDispatcher) {
        val journal = journalWithBreakfast(serverLog(42, todayKey()))
        val vm = newVm(journal)
        val toasts = collectToasts(vm)
        val foods = collectFoods(vm, "Breakfast")
        advanceUntilIdle()
        val cid = foods()[0].clientId!!
        journal.logsApi.dayFailure = java.io.IOException("offline")

        vm.removeFoodFromMeal("Breakfast", 0)
        advanceUntilIdle()

        assertEquals(0, foods().size)
        assertEquals(listOf<SyncOp>(SyncOp.DeleteLog(cid)), journal.ops())
        assertEquals(emptyList<Int>(), toasts.map { it.messageRes })
    }

    @Test
    fun editPendingFoodIsAllowed() = runTest(testDispatcher) {
        val journal = TestJournal().apply { logsApi.dayFailure = java.io.IOException("offline") }
        val vm = newVm(journal)
        val foods = collectFoods(vm, "Breakfast")
        advanceUntilIdle()
        vm.addFoodToMeal("Breakfast", LoggedFood(bread, 60))
        advanceUntilIdle()
        assertTrue(foods()[0].pending)

        vm.updateFoodPortion("Breakfast", 0, 175, unit = "slice", unitQty = 3f)
        advanceUntilIdle()

        assertEquals(175, foods()[0].grams)
        assertEquals("slice", foods()[0].unit)
        // Not sent yet: the edit is folded into the queued create.
        val create = journal.ops().single() as SyncOp.CreateLog
        assertEquals(175, create.body.grams)
        assertEquals("slice", create.body.unit)
        assertEquals(3f, create.body.unitQty)
    }

    @Test
    fun updateFoodPortion_updatesSyncedLogInPlaceAndQueuesUpdate() = runTest(testDispatcher) {
        val journal = journalWithBreakfast(serverLog(42, todayKey(), name = "Oats"), serverLog(43, todayKey()))
        val vm = newVm(journal)
        val foods = collectFoods(vm, "Breakfast")
        advanceUntilIdle()
        val cid = foods()[0].clientId!!

        vm.updateFoodPortion("Breakfast", 0, 175, unit = "slice", unitQty = 2f)
        advanceUntilIdle()

        assertEquals(2, foods().size)
        assertEquals("Oats", foods()[0].food.name)   // in place, no reordering
        assertEquals(175, foods()[0].grams)
        assertTrue(foods()[0].pending)
        val update = journal.ops().single() as SyncOp.UpdateLog
        assertEquals(cid, update.clientId)
        assertEquals(175, update.body.grams)
        assertEquals("slice", update.body.unit)
        assertEquals(2f, update.body.unitQty)
    }

    @Test
    fun updateRecipeLog_recomputesGramsFromIngredients() = runTest(testDispatcher) {
        val journal = journalWithBreakfast(serverLog(42, todayKey()))
        val vm = newVm(journal)
        val foods = collectFoods(vm, "Breakfast")
        advanceUntilIdle()
        val ingredients = listOf(
            FoodIngredient("Rice", 120f, 130f, 2.7f, 28f, 0.3f),
            FoodIngredient("Egg", 60f, 155f, 13f, 1.1f, 11f),
        )

        vm.updateRecipeLog("Breakfast", 0, ingredients)
        advanceUntilIdle()

        assertEquals(180, foods()[0].grams)
        val update = journal.ops().single() as SyncOp.UpdateLog
        assertEquals(180, update.body.grams)
        assertEquals(2, update.body.ingredients?.size)
    }

    @Test
    fun hydrationOfflineKeepsValue() = runTest(testDispatcher) {
        val journal = TestJournal().apply { logsApi.dayFailure = java.io.IOException("offline") }
        val vm = newVm(journal)
        val toasts = collectToasts(vm)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.hydrationTodayLiters.collect { } }
        advanceUntilIdle()
        toasts.clear()

        vm.incrementHydration()
        vm.incrementHydration()   // two quick taps: neither is lost
        advanceUntilIdle()

        assertEquals(0.5f, vm.hydrationTodayLiters.value)
        assertEquals(SyncOp.PutHydration(todayKey(), 0.5f), journal.ops().last())
        assertEquals(emptyList<Int>(), toasts.map { it.messageRes })
    }

    @Test
    fun weightOfflineKeepsValue() = runTest(testDispatcher) {
        val journal = TestJournal().apply { logsApi.dayFailure = java.io.IOException("offline") }
        val vm = newVm(journal)
        val toasts = collectToasts(vm)
        advanceUntilIdle()
        toasts.clear()

        vm.addWeightCheckIn(80f)
        advanceUntilIdle()

        assertEquals(80f, vm.weightHistory.value.last().weightKg)
        assertEquals(80f, vm.userProfile.value.currentWeightKg)
        assertEquals(SyncOp.PutWeight(todayKey(), 80f), journal.ops().single())
        assertEquals(emptyList<Int>(), toasts.map { it.messageRes })
    }

    @Test
    fun addWeightCheckIn_reloadsGoalsAfterSync() = runTest(testDispatcher) {
        val api = RecordingProfileApi()
        val journal = TestJournal()
        val vm = newVm(journal, profileApi = api)
        advanceUntilIdle()
        val before = api.getGoalsCalls

        vm.addWeightCheckIn(80f)
        advanceUntilIdle()
        assertEquals(before, api.getGoalsCalls)   // the server doesn't know the weight yet

        journal.repo.completeOp(journal.repo.nextOp()!!, null)
        advanceUntilIdle()
        assertEquals(before + 1, api.getGoalsCalls)
    }

    @Test
    fun restoreRemovedFoodKeepsPosition() = runTest(testDispatcher) {
        val key = todayKey()
        val journal = journalWithBreakfast(
            serverLog(1, key, name = "A"), serverLog(2, key, name = "B"), serverLog(3, key, name = "C"),
        )
        val vm = newVm(journal)
        val foods = collectFoods(vm, "Breakfast")
        advanceUntilIdle()
        val removed = foods()[1]

        vm.removeFoodFromMeal("Breakfast", 1)
        advanceUntilIdle()
        assertEquals(listOf("A", "C"), foods().map { it.food.name })

        vm.restoreRemovedFood("Breakfast", 1, removed)
        advanceUntilIdle()

        assertEquals(listOf("A", "B", "C"), foods().map { it.food.name })
        val restored = foods()[1]
        assertTrue(restored.pending)
        assertNotEquals(removed.clientId, restored.clientId)   // a new log, not the deleted one
        val create = journal.ops().last() as SyncOp.CreateLog
        assertEquals(restored.clientId, create.clientId)
        assertEquals(100, create.body.grams)
        assertEquals("b2", create.body.barcode)
    }

    @Test
    fun restoreRemovedFood_ignoresFoodStillPresent() = runTest(testDispatcher) {
        val journal = journalWithBreakfast(serverLog(1, todayKey()))
        val vm = newVm(journal)
        val foods = collectFoods(vm, "Breakfast")
        advanceUntilIdle()

        vm.restoreRemovedFood("Breakfast", 0, foods()[0])
        advanceUntilIdle()

        assertEquals(1, foods().size)
        assertEquals(emptyList<SyncOp>(), journal.ops())
    }

    @Test
    fun twoQuickAddsThenUndoOfTheSecond_removesTheSecond() = runTest(testDispatcher) {
        val journal = TestJournal()
        val vm = newVm(journal)
        val foods = collectFoods(vm, "Lunch")
        advanceUntilIdle()

        vm.addFoodToMealReturningIndex("Lunch", LoggedFood(sampleFood("Rice"), 200))
        val second = vm.addFoodToMealReturningIndex("Lunch", LoggedFood(sampleFood("Chicken"), 150))
        vm.removeFoodFromMeal("Lunch", second)   // undo before anything reached the mirror
        advanceUntilIdle()

        assertEquals(listOf("Rice"), foods().map { it.food.name })
        // The cancelled create never leaves the device.
        assertEquals(listOf("Rice"), journal.ops().map { (it as SyncOp.CreateLog).body.name })
    }

    @Test
    fun undoOfAddRightAfterAnotherLanded_removesTheNewOne() = runTest(testDispatcher) {
        val vm = newVm()
        val foods = collectFoods(vm, "Lunch")
        advanceUntilIdle()
        vm.addFoodToMealReturningIndex("Lunch", LoggedFood(sampleFood("Rice"), 200))
        advanceUntilIdle()

        val second = vm.addFoodToMealReturningIndex("Lunch", LoggedFood(sampleFood("Chicken"), 150))
        assertEquals(1, second)
        vm.removeFoodFromMeal("Lunch", second)
        advanceUntilIdle()

        assertEquals(listOf("Rice"), foods().map { it.food.name })
        // Index-based removal still works once the add is visible.
        vm.removeFoodFromMeal("Lunch", 0)
        advanceUntilIdle()
        assertEquals(emptyList<String>(), foods().map { it.food.name })
    }

    @Test
    fun localWriteFailureToastsErrorSave() = runTest(testDispatcher) {
        val journal = TestJournal()
        val vm = newVm(journal)
        val toasts = collectToasts(vm)
        advanceUntilIdle()
        journal.failWrites = true

        vm.addFoodToMeal("Lunch", LoggedFood(bread, 60))
        vm.incrementHydration()
        vm.setDayComplete(java.util.Date(), true)
        advanceUntilIdle()

        assertEquals(List(3) { R.string.error_save }, toasts.map { it.messageRes })
    }

    @Test
    fun goalsReloadOnlyOnceTheWeightItselfIsSynced() = runTest(testDispatcher) {
        val api = RecordingProfileApi()
        val journal = TestJournal()
        val vm = newVm(journal, profileApi = api)
        advanceUntilIdle()
        val before = api.getGoalsCalls

        vm.incrementHydration()
        advanceUntilIdle()
        vm.addWeightCheckIn(80f)
        advanceUntilIdle()

        journal.repo.completeOp(journal.repo.nextOp()!!, null)   // hydration of the same date
        advanceUntilIdle()
        assertEquals(before, api.getGoalsCalls)

        journal.repo.completeOp(journal.repo.nextOp()!!, null)   // the weight
        advanceUntilIdle()
        assertEquals(before + 1, api.getGoalsCalls)

        vm.incrementHydration()
        advanceUntilIdle()
        journal.repo.completeOp(journal.repo.nextOp()!!, null)   // date already settled: no reload
        advanceUntilIdle()
        assertEquals(before + 1, api.getGoalsCalls)
    }

    // ── Journal: events ──────────────────────────────────────────────────────

    @Test
    fun addFoodEmitsFoodLogsChanged() = runTest(testDispatcher) {
        val vm = newVm()
        val count = countEmissions(vm.foodLogsChanged)
        advanceUntilIdle()
        vm.addFoodToMeal("Lunch", LoggedFood(bread, 60))
        advanceUntilIdle()
        assertEquals(1, count())
    }

    @Test
    fun removeFoodEmitsFoodLogsChanged() = runTest(testDispatcher) {
        val vm = newVm(journalWithBreakfast(serverLog(42, todayKey())))
        collectFoods(vm, "Breakfast")
        val count = countEmissions(vm.foodLogsChanged)
        advanceUntilIdle()
        vm.removeFoodFromMeal("Breakfast", 0)
        advanceUntilIdle()
        assertEquals(1, count())
    }

    @Test
    fun syncedEventEmitsFoodLogsChanged() = runTest(testDispatcher) {
        val journal = TestJournal()
        val vm = newVm(journal)
        val logs = countEmissions(vm.foodLogsChanged)
        val statuses = countEmissions(vm.dayStatusChanged)
        advanceUntilIdle()
        vm.addFoodToMeal("Lunch", LoggedFood(bread, 60))
        advanceUntilIdle()
        assertEquals(1, logs())

        journal.repo.completeOp(journal.repo.nextOp()!!, null)
        advanceUntilIdle()

        assertEquals(2, logs())
        assertEquals(1, statuses())
    }

    @Test
    fun rejectedEventShowsSnackbar() = runTest(testDispatcher) {
        val journal = TestJournal()
        val vm = newVm(journal)
        val toasts = collectToasts(vm)
        val foods = collectFoods(vm, "Lunch")
        advanceUntilIdle()
        vm.addFoodToMeal("Lunch", LoggedFood(bread, 60))
        advanceUntilIdle()

        journal.repo.rejectOp(journal.repo.nextOp()!!, SyncOutcome.DROP_NOTIFY)
        advanceUntilIdle()

        assertEquals(listOf(R.string.sync_rejected), toasts.map { it.messageRes })
        assertEquals(0, foods().size)
    }

    @Test
    fun addFoodMarksMealLoggedImmediately() = runTest(testDispatcher) {
        val journal = TestJournal().apply { logsApi.dayFailure = java.io.IOException("offline") }
        val tracker = newTracker()
        val vm = newVm(journal, tracker = tracker)
        advanceUntilIdle()

        vm.addFoodToMeal("Lunch", LoggedFood(bread, 60))
        advanceUntilIdle()

        assertTrue(tracker.wasLogged(java.time.LocalDate.now(), "Lunch"))
        assertFalse(tracker.wasLogged(java.time.LocalDate.now(), "Dinner"))
    }

    // ── Copy (server copy until the copy sheet uses the mirror) ─────────────────

    @Test
    fun refreshAfterServerCopy_showsCopiedRows_andUndoQueuesDeletes() = runTest(testDispatcher) {
        val key = todayKey()
        val journal = journalWithBreakfast(serverLog(1, key, name = "Oats"))
        val vm = newVm(journal)
        val breakfast = collectFoods(vm, "Breakfast")
        val dinner = collectFoods(vm, "Dinner")
        val changes = countEmissions(vm.foodLogsChanged)
        advanceUntilIdle()

        // POST /food-logs/copy created two rows on the server.
        val copied = listOf(serverLog(100, key, "BREAKFAST", "Eggs"), serverLog(101, key, "DINNER", "Rice"))
        journal.logsApi.days[key] = DayResponseDto(
            mapOf("breakfast" to listOf(serverLog(1, key, name = "Oats"), copied[0]), "dinner" to listOf(copied[1])),
            0f, null,
        )
        vm.refreshAfterServerCopy(key, copied.map { it.meal })
        advanceUntilIdle()
        assertEquals(listOf("Oats", "Eggs"), breakfast().map { it.food.name })
        assertEquals(listOf("Rice"), dinner().map { it.food.name })
        assertEquals(1, changes())

        vm.undoCopy(key, copied.map { it.clientId })
        advanceUntilIdle()

        assertEquals(listOf("Oats"), breakfast().map { it.food.name })
        assertEquals(emptyList<String>(), dinner().map { it.food.name })
        assertEquals(copied.map { SyncOp.DeleteLog(it.clientId) }, journal.ops())
        assertEquals(2, changes())
    }

    @Test
    fun undoCopy_ofUnsyncedCopies_leavesNothingQueued() = runTest(testDispatcher) {
        val journal = TestJournal().apply { logsApi.dayFailure = java.io.IOException("offline") }
        val vm = newVm(journal)
        val dinner = collectFoods(vm, "Dinner")
        advanceUntilIdle()
        val ids = journal.repo.copyLogs(java.time.LocalDate.now(), listOf("Dinner" to LoggedFood(bread, 60)))
        advanceUntilIdle()
        assertEquals(1, dinner().size)

        vm.undoCopy(todayKey(), ids)
        advanceUntilIdle()

        assertEquals(0, dinner().size)
        assertEquals(emptyList<SyncOp>(), journal.ops())
    }

    // ── Reset ────────────────────────────────────────────────────────────────

    @Test
    fun resetUserState_clearsProfile_andClearedMirrorShowsEmptyDay() = runTest(testDispatcher) {
        val journal = journalWithBreakfast(serverLog(1, todayKey()))
        val vm = newVm(journal)
        val foods = collectFoods(vm, "Breakfast")
        vm.updateUserProfile(UserProfile(name = "Ana", age = 25))
        advanceUntilIdle()
        assertEquals(1, foods().size)

        // Logout: the mirror is wiped, then UI state.
        journal.repo.clearAll()
        vm.resetUserState()
        journal.logsApi.days.clear()   // account B has nothing that day
        vm.setSelectedHomeDate(java.util.Date())
        advanceUntilIdle()

        assertEquals(0, foods().size)
        assertEquals(UserProfile(), vm.userProfile.value)
    }

    // ── Hydration goal / prefs, profile ─────────────────────────────────────────

    @Test
    fun hydrationGoal_fallsBackTo2_5WhenGoalsMissing() = runTest(testDispatcher) {
        viewModel.loadGoals()
        advanceUntilIdle()
        assertEquals(2.5f, viewModel.hydrationGoalLiters.value)
    }

    @Test
    fun hydrationGoal_comesFromGoals() = runTest(testDispatcher) {
        val api = RecordingProfileApi().apply { goals = goalsWithHydration(3.5f) }
        val vm = newVm(profileApi = api)
        vm.loadGoals()
        advanceUntilIdle()
        assertEquals(3.5f, vm.hydrationGoalLiters.value)
    }

    @Test
    fun updateHydrationPrefs_savesProfileAndReloadsGoals() = runTest(testDispatcher) {
        val api = RecordingProfileApi().apply { goals = goalsWithHydration(3.0f) }
        val vm = newVm(profileApi = api)
        advanceUntilIdle()
        val before = api.getGoalsCalls
        vm.updateHydrationPrefs(takesCreatine = true, overrideL = null)
        advanceUntilIdle()
        assertEquals(1, api.saved.size)
        assertEquals(true, api.saved[0].takesCreatine)
        assertEquals(vm.userProfile.value.name, api.saved[0].name)
        assertEquals(before + 1, api.getGoalsCalls)
        assertEquals(3.0f, vm.hydrationGoalLiters.value)
    }

    @Test
    fun updateHydrationPrefs_debounced_coalescesToLastValue() = runTest(testDispatcher) {
        val api = RecordingProfileApi()
        val vm = newVm(profileApi = api)
        advanceUntilIdle()
        vm.updateHydrationPrefs(false, 3.0f, debounce = true)
        vm.updateHydrationPrefs(false, 3.1f, debounce = true)
        vm.updateHydrationPrefs(false, 3.2f, debounce = true)
        testScheduler.advanceTimeBy(599)
        testScheduler.runCurrent()
        assertEquals(0, api.saved.size)
        advanceUntilIdle()
        assertEquals(1, api.saved.size)
        assertEquals(3.2f, api.saved[0].hydrationGoalOverrideL)
    }

    @Test
    fun updateHydrationPrefs_immediateCancelsPendingDebounce() = runTest(testDispatcher) {
        val api = RecordingProfileApi()
        val vm = newVm(profileApi = api)
        advanceUntilIdle()
        vm.updateHydrationPrefs(false, 3.1f, debounce = true)
        vm.updateHydrationPrefs(false, null)
        advanceUntilIdle()
        assertEquals(1, api.saved.size)
        assertEquals(null, api.saved[0].hydrationGoalOverrideL)
        assertEquals(null, vm.userProfile.value.hydrationGoalOverrideL)
    }

    @Test
    fun updateHydrationPrefs_failureRevertsAndToasts() = runTest(testDispatcher) {
        val api = RecordingProfileApi().apply { saveFailure = java.io.IOException("boom") }
        val vm = newVm(profileApi = api)
        advanceUntilIdle()
        val toasts = collectToasts(vm)
        advanceUntilIdle()
        val initial = vm.userProfile.value
        vm.updateHydrationPrefs(takesCreatine = true, overrideL = 4.0f)
        assertEquals(true, vm.userProfile.value.takesCreatine)   // optimistic
        advanceUntilIdle()
        assertEquals(initial.takesCreatine, vm.userProfile.value.takesCreatine)
        assertEquals(initial.hydrationGoalOverrideL, vm.userProfile.value.hydrationGoalOverrideL)
        assertEquals(listOf(R.string.error_save_hydration_prefs), toasts.map { it.messageRes })
    }

    @Test
    fun resetUserState_cancelsPendingHydrationSave() = runTest(testDispatcher) {
        val api = RecordingProfileApi()
        val vm = newVm(profileApi = api)
        advanceUntilIdle()
        vm.updateHydrationPrefs(false, 3.1f, debounce = true)
        vm.resetUserState()
        advanceUntilIdle()
        assertEquals(0, api.saved.size)
    }

    @Test
    fun applyLoadedProfile_setsProfileAndLoadsGoals() = runTest(testDispatcher) {
        val api = RecordingProfileApi().apply { goals = goalsWithHydration(3.4f) }
        val vm = newVm(profileApi = api)
        advanceUntilIdle()
        val before = api.getGoalsCalls
        val loaded = UserProfile(name = "Ana", takesCreatine = true)
        vm.applyLoadedProfile(loaded)
        advanceUntilIdle()
        assertEquals(loaded, vm.userProfile.value)
        assertEquals(before + 1, api.getGoalsCalls)
        assertEquals(3.4f, vm.hydrationGoalLiters.value)
    }

    @Test
    fun applyLoadedProfile_sameProfileAgain_keepsLocalChangesAndDoesNotReload() = runTest(testDispatcher) {
        val api = RecordingProfileApi()
        val vm = newVm(profileApi = api)
        advanceUntilIdle()
        val loaded = UserProfile(name = "Ana")
        vm.applyLoadedProfile(loaded)
        advanceUntilIdle()
        vm.updateHydrationPrefs(takesCreatine = true, overrideL = 3.0f)
        advanceUntilIdle()
        val before = api.getGoalsCalls

        vm.applyLoadedProfile(loaded)   // e.g. Activity recreated, LaunchedEffect re-runs
        advanceUntilIdle()

        assertEquals(true, vm.userProfile.value.takesCreatine)
        assertEquals(3.0f, vm.userProfile.value.hydrationGoalOverrideL)
        assertEquals(before, api.getGoalsCalls)
    }

    @Test
    fun applyLoadedProfile_afterReset_appliesSameProfileAgain() = runTest(testDispatcher) {
        val api = RecordingProfileApi()
        val vm = newVm(profileApi = api)
        advanceUntilIdle()
        val loaded = UserProfile(name = "Ana")
        vm.applyLoadedProfile(loaded)
        advanceUntilIdle()
        vm.resetUserState()
        val before = api.getGoalsCalls

        vm.applyLoadedProfile(loaded)
        advanceUntilIdle()

        assertEquals(loaded, vm.userProfile.value)
        assertEquals(before + 1, api.getGoalsCalls)
    }
}
