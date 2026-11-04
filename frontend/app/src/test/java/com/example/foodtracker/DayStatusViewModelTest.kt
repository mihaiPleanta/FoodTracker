package com.example.foodtracker

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.foodtracker.api.DaysApi
import com.example.foodtracker.api.FoodApi
import com.example.foodtracker.api.LogsApi
import com.example.foodtracker.api.ProfileApi
import com.example.foodtracker.data.FakeSettingsRepository
import com.example.foodtracker.data.FoodRepository
import com.example.foodtracker.data.MealLogTracker
import com.example.foodtracker.data.cache.CachedProduct
import com.example.foodtracker.data.cache.CachedSearch
import com.example.foodtracker.data.cache.FoodCache
import com.example.foodtracker.model.DayResponseDto
import com.example.foodtracker.model.DayStatus
import com.example.foodtracker.model.DayStatusResponseDto
import com.example.foodtracker.model.DayStatusUpdateDto
import com.example.foodtracker.model.FoodItem
import com.example.foodtracker.model.FoodItemDto
import com.example.foodtracker.model.FoodLogCopyRequestDto
import com.example.foodtracker.model.FoodLogCreateDto
import com.example.foodtracker.model.FoodLogDeleteBatchDto
import com.example.foodtracker.model.FoodLogDeleteBatchResultDto
import com.example.foodtracker.model.FoodLogDto
import com.example.foodtracker.model.FoodLogSummaryDto
import com.example.foodtracker.model.FoodLogUpdateDto
import com.example.foodtracker.model.GoalsDto
import com.example.foodtracker.model.HydrationDto
import com.example.foodtracker.model.HydrationUpdateDto
import com.example.foodtracker.model.ProfileDto
import com.example.foodtracker.model.SearchResponseDto
import com.example.foodtracker.model.TdeeResponseDto
import com.example.foodtracker.model.WeightCheckInCreateDto
import com.example.foodtracker.model.WeightCheckInDto
import com.example.foodtracker.model.WeightCheckInListDto
import com.example.foodtracker.model.toDayStatus
import com.example.foodtracker.sync.SyncOp
import com.example.foodtracker.viewmodel.FoodViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.util.Calendar
import java.util.Date

private object DsNoOpCache : FoodCache {
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

private object DsNoOpFoodApi : FoodApi {
    override suspend fun searchFoods(query: String, page: Int, pageSize: Int): SearchResponseDto =
        SearchResponseDto(emptyList(), 0)
    override suspend fun getFoodByBarcode(barcode: String): FoodItemDto = throw UnsupportedOperationException()
}

private object DsNoOpProfileApi : ProfileApi {
    override suspend fun getProfile(): Response<ProfileDto> = throw UnsupportedOperationException()
    override suspend fun saveProfile(profile: ProfileDto): Response<ProfileDto> = throw UnsupportedOperationException()
    override suspend fun calculateTdee(profile: ProfileDto): Response<TdeeResponseDto> = throw UnsupportedOperationException()
    override suspend fun getGoals(): Response<GoalsDto> = Response.success(null)
    override suspend fun deleteAccount(): Response<Unit> = throw UnsupportedOperationException()
}

private fun dsLog(id: Long, date: String) = FoodLogDto(
    id = id, logDate = date, meal = "BREAKFAST", grams = 100, barcode = "b$id", name = "Food $id",
    kcal100g = 100f, protein100g = 1f, carbs100g = 1f, fat100g = 1f,
)

private fun dsDay(date: String, withFood: Boolean, status: String? = null) = DayResponseDto(
    foodsByMeal = if (withFood) mapOf("breakfast" to listOf(dsLog(date.replace("-", "").toLong(), date))) else emptyMap(),
    hydrationLiters = 0f,
    weightCheckIn = null,
    status = status,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DayStatusViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val dataStoreScope = CoroutineScope(testDispatcher + SupervisorJob())
    private val today = "2026-11-03"
    private val yesterday = "2026-11-02"
    private var now: Date = Calendar.getInstance().apply { clear(); set(2026, Calendar.NOVEMBER, 3, 12, 0) }.time
    private val journal = TestJournal(today = {
        java.time.LocalDate.parse(java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(now))
    })
    private val logs = journal.logsApi

    @get:Rule val tempFolder = TemporaryFolder()

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() {
        dataStoreScope.cancel()
        Dispatchers.resetMain()
    }

    private fun date(y: Int, m: Int, d: Int): Date =
        Calendar.getInstance().apply { clear(); set(y, m - 1, d, 12, 0) }.time

    private fun statusOps() = journal.ops().filterIsInstance<SyncOp.PutDayStatus>()

    private fun TestScope.newVm(): FoodViewModel {
        val vm = FoodViewModel(
            settingsRepository = FakeSettingsRepository(),
            mealLogTracker = MealLogTracker(
                PreferenceDataStoreFactory.create(
                    scope = dataStoreScope,
                    produceFile = { File(tempFolder.newFolder("t"), "meal_log_tracker.preferences_pb") },
                )
            ),
            foodRepository = FoodRepository(DsNoOpFoodApi, DsNoOpCache),
            journal = journal.repo,
            profileApi = DsNoOpProfileApi,
            clock = { now },
        )
        // WhileSubscribed flows only update while collected.
        vm.selectedDayStatus.launchIn(backgroundScope)
        vm.selectedDayHasFood.launchIn(backgroundScope)
        vm.showYesterdayPrompt.launchIn(backgroundScope)
        vm.selectedDayIsFuture.launchIn(backgroundScope)
        advanceUntilIdle()
        return vm
    }

    @Test
    fun toDayStatus_mapsWireValues() {
        assertEquals(DayStatus.COMPLETE, "complete".toDayStatus())
        assertEquals(DayStatus.INCOMPLETE, "incomplete".toDayStatus())
        assertNull(null.toDayStatus())
        assertNull("garbage".toDayStatus())
    }

    @Test
    fun selectedDayStatus_reflectsGetDay() = runTest(testDispatcher) {
        logs.days[today] = dsDay(today, withFood = true, status = "complete")
        val vm = newVm()
        assertEquals(DayStatus.COMPLETE, vm.selectedDayStatus.value)
        assertTrue(vm.selectedDayHasFood.value)
    }

    @Test
    fun selectedDayStatus_followsSelectedDate() = runTest(testDispatcher) {
        logs.days[today] = dsDay(today, withFood = true, status = "complete")
        logs.days["2026-10-20"] = dsDay("2026-10-20", withFood = false)
        val vm = newVm()
        vm.setSelectedHomeDate(date(2026, 10, 20))
        advanceUntilIdle()
        assertNull(vm.selectedDayStatus.value)
        assertFalse(vm.selectedDayHasFood.value)
    }

    @Test
    fun setDayComplete_writesMirrorAndQueuesStatus() = runTest(testDispatcher) {
        logs.days[today] = dsDay(today, withFood = true)
        val vm = newVm()

        vm.setDayComplete(now, true)
        advanceUntilIdle()

        assertEquals(DayStatus.COMPLETE, vm.selectedDayStatus.value)
        assertEquals(listOf(SyncOp.PutDayStatus(today, true)), statusOps())
        assertTrue(journal.scheduler.calls > 0)
    }

    @Test
    fun dayCompleteOfflineKeepsStatus() = runTest(testDispatcher) {
        logs.days[today] = dsDay(today, withFood = true, status = "incomplete")
        val vm = newVm()
        val toasts = mutableListOf<FoodViewModel.ToastEvent>()
        vm.toastEvents.onEach { toasts += it }.launchIn(backgroundScope)
        logs.dayFailure = IOException("offline")

        vm.setDayComplete(now, true)
        vm.setSelectedHomeDate(now)   // a refresh attempt while offline changes nothing
        advanceUntilIdle()

        assertEquals(DayStatus.COMPLETE, vm.selectedDayStatus.value)
        assertEquals(listOf(SyncOp.PutDayStatus(today, true)), statusOps())
        assertEquals(emptyList<Int>(), toasts.map { it.messageRes })
    }

    @Test
    fun clearDayStatus_nullsStatusAndQueuesClear() = runTest(testDispatcher) {
        logs.days[today] = dsDay(today, withFood = true, status = "complete")
        val vm = newVm()

        vm.clearDayStatus(now)
        advanceUntilIdle()

        assertEquals(listOf(SyncOp.PutDayStatus(today, null)), statusOps())
        assertNull(vm.selectedDayStatus.value)
    }

    @Test
    fun clearDayStatusOffline_staysCleared() = runTest(testDispatcher) {
        logs.days[today] = dsDay(today, withFood = true, status = "complete")
        val vm = newVm()
        logs.dayFailure = IOException("offline")

        vm.clearDayStatus(now)
        advanceUntilIdle()

        assertNull(vm.selectedDayStatus.value)
    }

    @Test
    fun yesterdayPrompt_shownWhenYesterdayHasFoodAndNoStatus() = runTest(testDispatcher) {
        logs.days[yesterday] = dsDay(yesterday, withFood = true)
        val vm = newVm()
        assertTrue(vm.showYesterdayPrompt.value)
        assertTrue(yesterday in logs.dayRequests)
    }

    @Test
    fun yesterdayPrompt_hiddenWhenYesterdayEmpty() = runTest(testDispatcher) {
        logs.days[yesterday] = dsDay(yesterday, withFood = false)
        assertFalse(newVm().showYesterdayPrompt.value)
    }

    @Test
    fun yesterdayPrompt_hiddenWhenYesterdayHasStatus() = runTest(testDispatcher) {
        logs.days[yesterday] = dsDay(yesterday, withFood = true, status = "incomplete")
        assertFalse(newVm().showYesterdayPrompt.value)
    }

    @Test
    fun yesterdayPrompt_shownFromMirrorWhenOffline() = runTest(testDispatcher) {
        // Yesterday was downloaded earlier (e.g. by the window refresh); now we're offline.
        logs.days[yesterday] = dsDay(yesterday, withFood = true)
        journal.repo.refreshDay(java.time.LocalDate.parse(yesterday))
        logs.dayFailure = IOException("offline")
        assertTrue(newVm().showYesterdayPrompt.value)
    }

    @Test
    fun yesterdayPrompt_hiddenWhenSelectedDateIsNotToday() = runTest(testDispatcher) {
        logs.days[yesterday] = dsDay(yesterday, withFood = true)
        val vm = newVm()
        assertTrue(vm.showYesterdayPrompt.value)
        vm.setSelectedHomeDate(date(2026, 10, 20))
        advanceUntilIdle()
        assertFalse(vm.showYesterdayPrompt.value)
        vm.setSelectedHomeDate(now)
        advanceUntilIdle()
        assertTrue(vm.showYesterdayPrompt.value)
    }

    @Test
    fun yesterday_isFetchedOnlyOncePerSession() = runTest(testDispatcher) {
        logs.days[yesterday] = dsDay(yesterday, withFood = true)
        val vm = newVm()
        vm.setSelectedHomeDate(date(2026, 10, 20))
        vm.setSelectedHomeDate(now)
        advanceUntilIdle()
        assertEquals(1, logs.dayRequests.count { it == yesterday })
    }

    @Test
    fun selectedDayIsFuture_trueOnlyAfterToday() = runTest(testDispatcher) {
        val vm = newVm()
        assertFalse(vm.selectedDayIsFuture.value)
        vm.setSelectedHomeDate(date(2026, 11, 4))
        advanceUntilIdle()
        assertTrue(vm.selectedDayIsFuture.value)
        vm.setSelectedHomeDate(date(2026, 11, 2))
        advanceUntilIdle()
        assertFalse(vm.selectedDayIsFuture.value)
    }

    @Test
    fun setDayComplete_futureDate_isNoOp() = runTest(testDispatcher) {
        val vm = newVm()
        val tomorrow = date(2026, 11, 4)
        vm.setSelectedHomeDate(tomorrow)
        advanceUntilIdle()

        vm.setDayComplete(tomorrow, true)
        advanceUntilIdle()

        assertTrue(statusOps().isEmpty())
        assertNull(vm.selectedDayStatus.value)
    }

    @Test
    fun yesterdayPrompt_afterMidnight_asksAboutNewYesterday() = runTest(testDispatcher) {
        logs.days[yesterday] = dsDay(yesterday, withFood = true)
        logs.days[today] = dsDay(today, withFood = true)
        val vm = newVm()
        assertTrue(vm.showYesterdayPrompt.value)

        now = date(2026, 11, 4)
        vm.setSelectedHomeDate(now)
        advanceUntilIdle()

        assertTrue(vm.showYesterdayPrompt.value)
        vm.answerYesterdayPrompt(true)
        advanceUntilIdle()
        assertEquals(listOf(SyncOp.PutDayStatus(today, true)), statusOps())
    }

    @Test
    fun yesterdayPrompt_afterMidnight_staleHasFoodDoesNotLeak() = runTest(testDispatcher) {
        logs.days[yesterday] = dsDay(yesterday, withFood = true)
        val vm = newVm()
        assertTrue(vm.showYesterdayPrompt.value)

        now = date(2026, 11, 4) // new yesterday (11-03) has no food
        vm.setSelectedHomeDate(now)
        advanceUntilIdle()

        assertFalse(vm.showYesterdayPrompt.value)
    }

    @Test
    fun yesterdayPrompt_answerForDayD_doesNotSuppressDayDPlus1() = runTest(testDispatcher) {
        logs.days[yesterday] = dsDay(yesterday, withFood = true)
        logs.days[today] = dsDay(today, withFood = true)
        val vm = newVm()
        vm.answerYesterdayPrompt(false)
        advanceUntilIdle()
        assertFalse(vm.showYesterdayPrompt.value)

        now = date(2026, 11, 4)
        vm.setSelectedHomeDate(now)
        advanceUntilIdle()

        assertTrue(vm.showYesterdayPrompt.value)
    }

    @Test
    fun answerYesterdayPrompt_false_queuesIncomplete_andHidesPrompt() = runTest(testDispatcher) {
        logs.days[yesterday] = dsDay(yesterday, withFood = true)
        val vm = newVm()

        vm.answerYesterdayPrompt(false)
        advanceUntilIdle()

        assertEquals(listOf(SyncOp.PutDayStatus(yesterday, false)), statusOps())
        assertFalse(vm.showYesterdayPrompt.value)
    }

    @Test
    fun answerYesterdayPrompt_true_queuesComplete() = runTest(testDispatcher) {
        logs.days[yesterday] = dsDay(yesterday, withFood = true)
        val vm = newVm()
        vm.answerYesterdayPrompt(true)
        advanceUntilIdle()
        assertEquals(listOf(SyncOp.PutDayStatus(yesterday, true)), statusOps())
        assertFalse(vm.showYesterdayPrompt.value)
    }

    @Test
    fun answerYesterdayPrompt_offline_staysHidden() = runTest(testDispatcher) {
        logs.days[yesterday] = dsDay(yesterday, withFood = true)
        val vm = newVm()
        logs.dayFailure = IOException("offline")
        vm.answerYesterdayPrompt(true)
        advanceUntilIdle()
        assertFalse(vm.showYesterdayPrompt.value)
    }

    @Test
    fun resetUserState_clearsAnsweredFlag_andClearedMirrorHidesStatus() = runTest(testDispatcher) {
        logs.days[today] = dsDay(today, withFood = true, status = "complete")
        logs.days[yesterday] = dsDay(yesterday, withFood = true)
        val vm = newVm()
        vm.answerYesterdayPrompt(true)
        advanceUntilIdle()

        // Logout: the mirror is wiped, then UI state.
        journal.repo.clearAll()
        vm.resetUserState()
        advanceUntilIdle()
        assertNull(vm.selectedDayStatus.value)
        // Next account: yesterday is fetched again and the prompt can show again.
        vm.setSelectedHomeDate(now)
        advanceUntilIdle()
        assertEquals(DayStatus.COMPLETE, vm.selectedDayStatus.value)
        assertTrue(vm.showYesterdayPrompt.value)
    }

    @Test
    fun dayStatusChanged_emitsOnEveryLocalWrite_evenOffline() = runTest(testDispatcher) {
        logs.days[today] = dsDay(today, withFood = true)
        val vm = newVm()
        var emissions = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.dayStatusChanged.collect { emissions++ }
        }

        vm.setDayComplete(now, true)
        advanceUntilIdle()
        assertEquals(1, emissions)

        vm.clearDayStatus(now)
        advanceUntilIdle()
        assertEquals(2, emissions)

        logs.dayFailure = IOException("offline")
        vm.setDayComplete(now, true)
        advanceUntilIdle()
        assertEquals(3, emissions)
    }
}
