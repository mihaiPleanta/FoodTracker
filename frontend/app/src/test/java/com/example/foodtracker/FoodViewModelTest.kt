package com.example.foodtracker

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.foodtracker.api.FoodApi
import com.example.foodtracker.api.LogsApi
import com.example.foodtracker.api.ProfileApi
import com.example.foodtracker.data.FakeSettingsRepository
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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
}

private object NoOpFoodApi : FoodApi {
    override suspend fun searchFoods(query: String, page: Int, pageSize: Int): SearchResponseDto =
        SearchResponseDto(emptyList(), 0)
    override suspend fun getFoodByBarcode(barcode: String): FoodItemDto =
        throw UnsupportedOperationException("NoOpFoodApi")
}

/**
 * Fake LogsApi for testing the PATCH-grams flow. getDay seeds one breakfast item with a real id
 * (so updateFoodPortion has something to edit without going through the add path / markLogged);
 * updateFoodLog echoes the new grams and records the call. The rest throw.
 */
private class FakeLogsApi : LogsApi {
    var lastUpdate: Pair<Long, Int>? = null
    var lastUpdateBody: FoodLogUpdateDto? = null
    var lastCreate: FoodLogCreateDto? = null
    var failUpdateWith: Throwable? = null
    var seedUnit: String? = null
    var seedUnitQty: Float? = null

    override suspend fun getDay(date: String): DayResponseDto = DayResponseDto(
        foodsByMeal = mapOf(
            "breakfast" to listOf(
                FoodLogDto(
                    id = 42L, logDate = date, meal = "BREAKFAST", grams = 100,
                    barcode = "111", name = "Oats", brand = null, imageUrl = null,
                    categories = emptyList(), kcal100g = 100f, protein100g = 5f,
                    carbs100g = 10f, fat100g = 2f, unit = seedUnit, unitQty = seedUnitQty,
                )
            )
        ),
        hydrationLiters = 0f,
        weightCheckIn = null,
    )

    override suspend fun createFoodLog(body: FoodLogCreateDto): FoodLogDto {
        lastCreate = body
        return FoodLogDto(
            id = 7L, logDate = body.logDate, meal = body.meal, grams = body.grams,
            barcode = body.barcode, name = body.name, kcal100g = body.kcal100g,
            protein100g = body.protein100g, carbs100g = body.carbs100g, fat100g = body.fat100g,
            unit = body.unit, unitQty = body.unitQty,
        )
    }

    override suspend fun updateFoodLog(id: Long, body: FoodLogUpdateDto): FoodLogDto {
        failUpdateWith?.let { throw it }
        lastUpdateBody = body
        lastUpdate = id to body.grams
        return FoodLogDto(
            id = id, logDate = "2026-06-03", meal = "BREAKFAST", grams = body.grams,
            barcode = "111", name = "Oats", brand = null, imageUrl = null, categories = emptyList(),
            kcal100g = 100f, protein100g = 5f, carbs100g = 10f, fat100g = 2f,
        )
    }

    override suspend fun deleteFoodLog(id: Long): retrofit2.Response<Unit> =
        retrofit2.Response.success(null)

    override suspend fun putHydration(date: String, body: HydrationUpdateDto): HydrationDto =
        throw UnsupportedOperationException()
    override suspend fun postWeightCheckIn(body: WeightCheckInCreateDto): WeightCheckInDto =
        throw UnsupportedOperationException()
    override suspend fun getWeightCheckIns(from: String, to: String): WeightCheckInListDto =
        throw UnsupportedOperationException()
}

/**
 * A LogsApi whose getDay response can be swapped at runtime, to simulate switching
 * accounts (account A logs in, logs out, account B logs in on the same process).
 */
private class SwitchableLogsApi(initial: DayResponseDto) : LogsApi {
    var day: DayResponseDto = initial

    override suspend fun getDay(date: String): DayResponseDto = day
    override suspend fun createFoodLog(body: FoodLogCreateDto): FoodLogDto =
        throw UnsupportedOperationException()
    override suspend fun updateFoodLog(id: Long, body: FoodLogUpdateDto): FoodLogDto =
        throw UnsupportedOperationException()
    override suspend fun deleteFoodLog(id: Long): retrofit2.Response<Unit> =
        retrofit2.Response.success(null)
    override suspend fun putHydration(date: String, body: HydrationUpdateDto): HydrationDto =
        throw UnsupportedOperationException()
    override suspend fun postWeightCheckIn(body: WeightCheckInCreateDto): WeightCheckInDto =
        throw UnsupportedOperationException()
    override suspend fun getWeightCheckIns(from: String, to: String): WeightCheckInListDto =
        throw UnsupportedOperationException()
}

private fun dayWithOneBreakfast() = DayResponseDto(
    foodsByMeal = mapOf(
        "breakfast" to listOf(
            FoodLogDto(
                id = 1L, logDate = "2026-06-24", meal = "BREAKFAST", grams = 100,
                barcode = "111", name = "Oats", brand = null, imageUrl = null,
                categories = emptyList(), kcal100g = 100f, protein100g = 5f,
                carbs100g = 10f, fat100g = 2f,
            )
        )
    ),
    hydrationLiters = 0f,
    weightCheckIn = null,
)

private fun emptyDay() = DayResponseDto(emptyMap(), 0f, null)

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
        val trackerStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { File(tempFolder.newFolder("tracker"), "meal_log_tracker.preferences_pb") }
        )
        viewModel = FoodViewModel(
            settingsRepository = FakeSettingsRepository(),
            mealLogTracker = MealLogTracker(trackerStore),
            foodRepository = FoodRepository(NoOpFoodApi, NoOpCache),
            logsApi = SwitchableLogsApi(emptyDay()),
            profileApi = NoOpProfileApi,
        )
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun updateUserProfile_updatesValue() {
        val profile = UserProfile(name = "Ana", age = 25, gender = Gender.FEMALE)
        viewModel.updateUserProfile(profile)
        assertEquals(profile, viewModel.userProfile.value)
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

    @Test
    fun addFoodToMealReturningIndex_returnsZeroForFirstAdd() {
        val index = viewModel.addFoodToMealReturningIndex(
            "Breakfast",
            LoggedFood(sampleFood("Oats"), 100)
        )
        assertEquals(0, index)
    }

    @Test
    fun loadMore_appendsNextPage_andUpdatesHasMore() = runTest(testDispatcher) {
        val store = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { File(tempFolder.newFolder("tracker2"), "meal_log_tracker.preferences_pb") }
        )
        val vm = FoodViewModel(
            settingsRepository = FakeSettingsRepository(),
            mealLogTracker = MealLogTracker(store),
            foodRepository = FoodRepository(PagingFoodApi, NoOpCache),
            logsApi = SwitchableLogsApi(emptyDay()),
            profileApi = NoOpProfileApi,
        )

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

    @Test
    fun updateFoodPortion_updatesGramsInPlace() = runTest(testDispatcher) {
        val fakeLogs = FakeLogsApi()
        val store = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { File(tempFolder.newFolder("tracker3"), "meal_log_tracker.preferences_pb") }
        )
        val vm = FoodViewModel(
            settingsRepository = FakeSettingsRepository(),
            mealLogTracker = MealLogTracker(store),
            foodRepository = FoodRepository(NoOpFoodApi, NoOpCache),
            logsApi = fakeLogs,
            profileApi = NoOpProfileApi,
        )
        // Capture latest emission (WhileSubscribed needs an active collector).
        var foods: List<LoggedFood> = emptyList()
        val job = launch { vm.getFoodsFlow("Breakfast").collect { foods = it } }
        advanceUntilIdle()  // drain init loadDay → seeds one breakfast item (id=42, 100g)

        assertEquals(1, foods.size)
        assertEquals(100, foods[0].grams)

        vm.updateFoodPortion("Breakfast", 0, 175, unit = null, unitQty = null)
        advanceUntilIdle()  // PATCH resolves

        assertEquals(1, foods.size)                     // still one item, in place
        assertEquals(175, foods[0].grams)               // grams updated
        assertEquals(42L to 175, fakeLogs.lastUpdate)   // PATCH sent id + new grams
        job.cancel()
    }


    private fun TestScope.newVm(fake: FakeLogsApi, folder: String) = FoodViewModel(
        settingsRepository = FakeSettingsRepository(),
        mealLogTracker = MealLogTracker(
            PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
                produceFile = { File(tempFolder.newFolder(folder), "meal_log_tracker.preferences_pb") }
            )
        ),
        foodRepository = FoodRepository(NoOpFoodApi, NoOpCache),
        logsApi = fake,
        profileApi = NoOpProfileApi,
    )

    @Test
    fun addFood_sendsUnitAndQty() = runTest(testDispatcher) {
        val fake = FakeLogsApi()
        val vm = newVm(fake, "addUnit")
        advanceUntilIdle()
        val food = FoodItem(
            barcode = "999", name = "Bread", brand = null, imageUrl = null,
            categories = emptyList(), per100g = 250, protein100g = 8f, carbs100g = 45f, fat100g = 3f,
        )
        vm.addFoodToMeal("Lunch", LoggedFood(food, 60, unit = "slice", unitQty = 2f))
        advanceUntilIdle()
        assertEquals("slice", fake.lastCreate?.unit)
        assertEquals(2f, fake.lastCreate?.unitQty)
    }

    @Test
    fun updateFoodPortion_patchesUnit() = runTest(testDispatcher) {
        val fake = FakeLogsApi()
        val vm = newVm(fake, "patchUnit")
        var foods: List<LoggedFood> = emptyList()
        val job = launch { vm.getFoodsFlow("Breakfast").collect { foods = it } }
        advanceUntilIdle()

        vm.updateFoodPortion("Breakfast", 0, 60, unit = "slice", unitQty = 2f)
        advanceUntilIdle()

        assertEquals(60, fake.lastUpdateBody?.grams)
        assertEquals("slice", fake.lastUpdateBody?.unit)
        assertEquals(2f, fake.lastUpdateBody?.unitQty)
        assertEquals(60, foods[0].grams)
        assertEquals("slice", foods[0].unit)
        assertEquals(2f, foods[0].unitQty)
        job.cancel()
    }

    @Test
    fun updateFoodPortion_rollbackRestoresUnit() = runTest(testDispatcher) {
        val fake = FakeLogsApi().apply {
            seedUnit = "tsp"; seedUnitQty = 3f
            failUpdateWith = java.io.IOException("offline")
        }
        val vm = newVm(fake, "rollbackUnit")
        var foods: List<LoggedFood> = emptyList()
        val job = launch { vm.getFoodsFlow("Breakfast").collect { foods = it } }
        advanceUntilIdle()
        assertEquals("tsp", foods[0].unit)

        vm.updateFoodPortion("Breakfast", 0, 60, unit = "slice", unitQty = 2f)
        advanceUntilIdle()

        assertEquals(100, foods[0].grams)
        assertEquals("tsp", foods[0].unit)
        assertEquals(3f, foods[0].unitQty)
        job.cancel()
    }

    @Test
    fun resetUserState_clearsProfileAndLoggedMeals() = runTest(testDispatcher) {
        val store = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { File(tempFolder.newFolder("trackerReset"), "meal_log_tracker.preferences_pb") }
        )
        val vm = FoodViewModel(
            settingsRepository = FakeSettingsRepository(),
            mealLogTracker = MealLogTracker(store),
            foodRepository = FoodRepository(NoOpFoodApi, NoOpCache),
            logsApi = SwitchableLogsApi(dayWithOneBreakfast()),
            profileApi = NoOpProfileApi,
        )
        var foods: List<LoggedFood> = emptyList()
        val job = launch { vm.getFoodsFlow("Breakfast").collect { foods = it } }
        vm.updateUserProfile(UserProfile(name = "Ana", age = 25))
        advanceUntilIdle()   // init loadDay(today) → account A's one breakfast item
        assertEquals(1, foods.size)
        assertEquals("Ana", vm.userProfile.value.name)

        vm.resetUserState()
        advanceUntilIdle()

        assertEquals(0, foods.size)                 // logged meals cleared
        assertEquals(UserProfile(), vm.userProfile.value)   // profile cleared
        job.cancel()
    }

    @Test
    fun resetUserState_clearsLoadedDates_soNextLoadRefetches() = runTest(testDispatcher) {
        // Regression: account A's logged day must not leak into account B after logout.
        val logs = SwitchableLogsApi(dayWithOneBreakfast())
        val store = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { File(tempFolder.newFolder("trackerReset2"), "meal_log_tracker.preferences_pb") }
        )
        val vm = FoodViewModel(
            settingsRepository = FakeSettingsRepository(),
            mealLogTracker = MealLogTracker(store),
            foodRepository = FoodRepository(NoOpFoodApi, NoOpCache),
            logsApi = logs,
            profileApi = NoOpProfileApi,
        )
        var foods: List<LoggedFood> = emptyList()
        val job = launch { vm.getFoodsFlow("Breakfast").collect { foods = it } }
        advanceUntilIdle()   // account A logged in → one breakfast item for today
        assertEquals(1, foods.size)

        // Logout + a different account whose same day is empty.
        vm.resetUserState()
        logs.day = emptyDay()
        vm.setSelectedHomeDate(java.util.Date())   // what HomeScreen does on entry
        advanceUntilIdle()

        // Without clearing _loadedDates, loadDay() short-circuits and account A's
        // stale item stays on screen. After reset it must re-fetch → empty.
        assertEquals(0, foods.size)
        job.cancel()
    }

    @Test
    fun addFoodToMealReturningIndex_returnsIncrementingIndexes() {
        val firstIndex = viewModel.addFoodToMealReturningIndex(
            "Lunch",
            LoggedFood(sampleFood("Rice"), 200)
        )
        val secondIndex = viewModel.addFoodToMealReturningIndex(
            "Lunch",
            LoggedFood(sampleFood("Chicken"), 150)
        )
        val thirdIndex = viewModel.addFoodToMealReturningIndex(
            "Lunch",
            LoggedFood(sampleFood("Broccoli"), 80)
        )
        assertEquals(0, firstIndex)
        assertEquals(1, secondIndex)
        assertEquals(2, thirdIndex)
    }
}
