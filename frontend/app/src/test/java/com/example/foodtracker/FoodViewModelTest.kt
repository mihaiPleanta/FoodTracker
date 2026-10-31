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
    override suspend fun getFoodLogSummary(from: String, to: String): com.example.foodtracker.model.FoodLogSummaryDto =
        throw UnsupportedOperationException()
    override suspend fun copyFoodLogs(body: com.example.foodtracker.model.FoodLogCopyRequestDto): List<FoodLogDto> =
        throw UnsupportedOperationException()
    var lastDeleteBatch: com.example.foodtracker.model.FoodLogDeleteBatchDto? = null
    var failDeleteBatchWith: Throwable? = null
    override suspend fun deleteFoodLogs(body: com.example.foodtracker.model.FoodLogDeleteBatchDto): com.example.foodtracker.model.FoodLogDeleteBatchResultDto {
        failDeleteBatchWith?.let { throw it }
        lastDeleteBatch = body
        return com.example.foodtracker.model.FoodLogDeleteBatchResultDto(body.ids.size)
    }
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
    override suspend fun getFoodLogSummary(from: String, to: String): com.example.foodtracker.model.FoodLogSummaryDto =
        throw UnsupportedOperationException()
    override suspend fun copyFoodLogs(body: com.example.foodtracker.model.FoodLogCopyRequestDto): List<FoodLogDto> =
        throw UnsupportedOperationException()
    override suspend fun deleteFoodLogs(body: com.example.foodtracker.model.FoodLogDeleteBatchDto): com.example.foodtracker.model.FoodLogDeleteBatchResultDto =
        throw UnsupportedOperationException()
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
    fun persistedSettings_firstValueIsStoredOneNotDefaults() = runTest(testDispatcher) {
        // Reminder sync must never run on the fabricated AppSettings() placeholder that
        // appSettings starts with before DataStore loads — it would reschedule custom times.
        val stored = AppSettings(lunchReminder = MealReminder(enabled = true, minuteOfDay = 750))
        val slowRepo = object : SettingsRepository {
            override val settings = flow { delay(1_000); emit(stored) }
            override suspend fun update(settings: AppSettings) = Unit
        }
        val vm = FoodViewModel(
            settingsRepository = slowRepo,
            mealLogTracker = MealLogTracker(
                PreferenceDataStoreFactory.create(
                    scope = dataStoreScope,
                    produceFile = { File(tempFolder.newFolder("tracker2"), "meal_log_tracker.preferences_pb") }
                )
            ),
            foodRepository = FoodRepository(NoOpFoodApi, NoOpCache),
            logsApi = SwitchableLogsApi(emptyDay()),
            profileApi = NoOpProfileApi,
        )
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

    private fun hydrationVm(profileApi: ProfileApi, logsApi: LogsApi = SwitchableLogsApi(emptyDay())) = FoodViewModel(
        settingsRepository = FakeSettingsRepository(),
        mealLogTracker = MealLogTracker(
            PreferenceDataStoreFactory.create(
                scope = dataStoreScope,
                produceFile = { File(tempFolder.newFolder(), "meal_log_tracker.preferences_pb") }
            )
        ),
        foodRepository = FoodRepository(NoOpFoodApi, NoOpCache),
        logsApi = logsApi,
        profileApi = profileApi,
    )

    @Test
    fun hydrationGoal_fallsBackTo2_5WhenGoalsMissing() = runTest(testDispatcher) {
        viewModel.loadGoals()
        advanceUntilIdle()
        assertEquals(2.5f, viewModel.hydrationGoalLiters.value)
    }

    @Test
    fun hydrationGoal_comesFromGoals() = runTest(testDispatcher) {
        val api = RecordingProfileApi().apply { goals = goalsWithHydration(3.5f) }
        val vm = hydrationVm(api)
        vm.loadGoals()
        advanceUntilIdle()
        assertEquals(3.5f, vm.hydrationGoalLiters.value)
    }

    @Test
    fun updateHydrationPrefs_savesProfileAndReloadsGoals() = runTest(testDispatcher) {
        val api = RecordingProfileApi().apply { goals = goalsWithHydration(3.0f) }
        val vm = hydrationVm(api)
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
        val vm = hydrationVm(api)
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
        val vm = hydrationVm(api)
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
        val vm = hydrationVm(api)
        advanceUntilIdle()
        val toasts = mutableListOf<FoodViewModel.ToastEvent>()
        val job = launch { vm.toastEvents.collect { toasts += it } }
        advanceUntilIdle()
        val initial = vm.userProfile.value
        vm.updateHydrationPrefs(takesCreatine = true, overrideL = 4.0f)
        assertEquals(true, vm.userProfile.value.takesCreatine)   // optimistic
        advanceUntilIdle()
        assertEquals(initial.takesCreatine, vm.userProfile.value.takesCreatine)
        assertEquals(initial.hydrationGoalOverrideL, vm.userProfile.value.hydrationGoalOverrideL)
        assertEquals(listOf(R.string.error_save_hydration_prefs), toasts.map { it.messageRes })
        job.cancel()
    }

    @Test
    fun addWeightCheckIn_success_reloadsGoals() = runTest(testDispatcher) {
        val api = RecordingProfileApi()
        val logs = object : LogsApi by SwitchableLogsApi(emptyDay()) {
            override suspend fun postWeightCheckIn(body: WeightCheckInCreateDto) =
                WeightCheckInDto(body.date, body.weightKg)
        }
        val vm = hydrationVm(api, logs)
        advanceUntilIdle()
        val before = api.getGoalsCalls
        vm.addWeightCheckIn(80f)
        advanceUntilIdle()
        assertEquals(before + 1, api.getGoalsCalls)
    }

    @Test
    fun resetUserState_cancelsPendingHydrationSave() = runTest(testDispatcher) {
        val api = RecordingProfileApi()
        val vm = hydrationVm(api)
        advanceUntilIdle()
        vm.updateHydrationPrefs(false, 3.1f, debounce = true)
        vm.resetUserState()
        advanceUntilIdle()
        assertEquals(0, api.saved.size)
    }

    @Test
    fun applyLoadedProfile_setsProfileAndLoadsGoals() = runTest(testDispatcher) {
        val api = RecordingProfileApi().apply { goals = goalsWithHydration(3.4f) }
        val vm = hydrationVm(api)
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
        val vm = hydrationVm(api)
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
        val vm = hydrationVm(api)
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

    private fun copiedDto(id: Long, meal: String, name: String, date: String) = FoodLogDto(
        id = id, logDate = date, meal = meal, grams = 50,
        barcode = "b$id", name = name, brand = null, imageUrl = null,
        categories = emptyList(), kcal100g = 100f, protein100g = 5f,
        carbs100g = 10f, fat100g = 2f,
    )

    private fun todayKey() = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

    @Test
    fun appendCopiedLogs_addsToEndOfMealsOnGivenDate() = runTest(testDispatcher) {
        val vm = newVm(FakeLogsApi(), "copyAppend")
        val job = launch {
            launch { vm.breakfastFoods.collect { } }
            launch { vm.dinnerFoods.collect { } }
        }
        vm.setSelectedHomeDate(java.util.Date())
        advanceUntilIdle()
        val key = todayKey()
        vm.appendCopiedLogs(key, listOf(copiedDto(100, "BREAKFAST", "Eggs", key), copiedDto(101, "DINNER", "Rice", key)))
        advanceUntilIdle()
        assertEquals(listOf("Oats", "Eggs"), vm.breakfastFoods.value.map { it.food.name })
        assertEquals(listOf("Rice"), vm.dinnerFoods.value.map { it.food.name })
        job.cancel()
    }

    @Test
    fun appendCopiedLogs_onNonSelectedDate_doesNotChangeVisibleDay() = runTest(testDispatcher) {
        val vm = newVm(FakeLogsApi(), "copyOther")
        val job = launch { vm.breakfastFoods.collect { } }
        vm.setSelectedHomeDate(java.util.Date())
        advanceUntilIdle()
        vm.appendCopiedLogs("2020-01-01", listOf(copiedDto(100, "BREAKFAST", "Eggs", "2020-01-01")))
        advanceUntilIdle()
        assertEquals(listOf("Oats"), vm.breakfastFoods.value.map { it.food.name })
        job.cancel()
    }

    @Test
    fun undoCopy_removesRowsAndCallsDeleteBatch() = runTest(testDispatcher) {
        val fake = FakeLogsApi()
        val vm = newVm(fake, "copyUndo")
        val job = launch {
            launch { vm.breakfastFoods.collect { } }
            launch { vm.dinnerFoods.collect { } }
        }
        vm.setSelectedHomeDate(java.util.Date())
        advanceUntilIdle()
        val key = todayKey()
        vm.appendCopiedLogs(key, listOf(copiedDto(100, "BREAKFAST", "Eggs", key), copiedDto(101, "DINNER", "Rice", key)))
        vm.undoCopy(key, listOf(100L, 101L))
        advanceUntilIdle()
        assertEquals(listOf(100L, 101L), fake.lastDeleteBatch!!.ids)
        assertEquals(listOf("Oats"), vm.breakfastFoods.value.map { it.food.name })
        assertEquals(emptyList<String>(), vm.dinnerFoods.value.map { it.food.name })
        job.cancel()
    }

    @Test
    fun undoCopy_networkFailure_restoresRowsAndEmitsToast() = runTest(testDispatcher) {
        val fake = FakeLogsApi().apply { failDeleteBatchWith = java.io.IOException() }
        val vm = newVm(fake, "copyUndoFail")
        val toasts = mutableListOf<FoodViewModel.ToastEvent>()
        val job = launch {
            launch { vm.breakfastFoods.collect { } }
            launch { vm.toastEvents.collect { toasts += it } }
        }
        vm.setSelectedHomeDate(java.util.Date())
        advanceUntilIdle()
        val key = todayKey()
        vm.appendCopiedLogs(key, listOf(copiedDto(100, "BREAKFAST", "Eggs", key)))
        vm.undoCopy(key, listOf(100L))
        advanceUntilIdle()
        assertEquals(listOf("Oats", "Eggs"), vm.breakfastFoods.value.map { it.food.name })
        assertEquals(listOf(R.string.error_no_internet), toasts.map { it.messageRes })
        job.cancel()
    }
}
