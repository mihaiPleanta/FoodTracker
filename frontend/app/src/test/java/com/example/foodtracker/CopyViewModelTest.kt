package com.example.foodtracker

import com.example.foodtracker.api.LogsApi
import com.example.foodtracker.model.DayResponseDto
import com.example.foodtracker.model.DaySummaryDto
import com.example.foodtracker.model.FoodIngredient
import com.example.foodtracker.model.FoodLogCopyRequestDto
import com.example.foodtracker.model.FoodLogCreateDto
import com.example.foodtracker.model.FoodLogDeleteBatchDto
import com.example.foodtracker.model.FoodLogDeleteBatchResultDto
import com.example.foodtracker.model.FoodLogDto
import com.example.foodtracker.model.FoodLogIngredientDto
import com.example.foodtracker.model.FoodLogSummaryDto
import com.example.foodtracker.model.FoodLogUpdateDto
import com.example.foodtracker.model.HydrationDto
import com.example.foodtracker.model.HydrationUpdateDto
import com.example.foodtracker.model.MealSummaryDto
import com.example.foodtracker.model.WeightCheckInCreateDto
import com.example.foodtracker.model.WeightCheckInDto
import com.example.foodtracker.model.WeightCheckInListDto
import com.example.foodtracker.viewmodel.CopyMode
import com.example.foodtracker.viewmodel.CopyResult
import com.example.foodtracker.viewmodel.CopyViewModel
import com.example.foodtracker.viewmodel.SourceDayState
import com.example.foodtracker.viewmodel.FoodViewModel
import com.example.foodtracker.viewmodel.candidatesFrom
import com.example.foodtracker.viewmodel.stripDaysFor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.time.LocalDate

private fun log(id: Long, meal: String, grams: Int = 100, ingredients: List<FoodLogIngredientDto>? = null) = FoodLogDto(
    id = id, logDate = "2026-10-30", meal = meal, grams = grams, barcode = "b$id", name = "Food $id",
    kcal100g = 100f, protein100g = 1f, carbs100g = 1f, fat100g = 1f, ingredients = ingredients,
)

private fun summaryOf(vararg days: Pair<String, Map<String, MealSummaryDto>>) =
    FoodLogSummaryDto(days.map { DaySummaryDto(it.first, it.second) })

private fun dayOf(vararg meals: Pair<String, List<FoodLogDto>>) =
    DayResponseDto(meals.toMap(), 0f, null)

private class FakeCopyLogsApi : LogsApi {
    var summary = FoodLogSummaryDto(emptyList())
    val days = mutableMapOf<String, DayResponseDto>()
    val dayGates = mutableMapOf<String, CompletableDeferred<Unit>>()
    var failCopy: Throwable? = null
    var summaryGate: CompletableDeferred<Unit>? = null
    var failSummary = false
    var copyGate: CompletableDeferred<Unit>? = null
    var copyResult: List<FoodLogDto> = emptyList()
    val summaryRequests = mutableListOf<Pair<String, String>>()
    val copyRequests = mutableListOf<FoodLogCopyRequestDto>()

    override suspend fun getDay(date: String): DayResponseDto {
        dayGates[date]?.await()
        return days[date] ?: dayOf()
    }
    override suspend fun getFoodLogSummary(from: String, to: String): FoodLogSummaryDto {
        summaryRequests += from to to
        summaryGate?.await()
        if (failSummary) throw IOException("offline")
        return summary
    }
    override suspend fun copyFoodLogs(body: FoodLogCopyRequestDto): List<FoodLogDto> {
        copyRequests += body
        copyGate?.await()
        failCopy?.let { throw it }
        return copyResult
    }
    override suspend fun createFoodLog(body: FoodLogCreateDto): FoodLogDto = error("unused")
    override suspend fun updateFoodLog(id: Long, body: FoodLogUpdateDto): FoodLogDto = error("unused")
    override suspend fun deleteFoodLogs(body: FoodLogDeleteBatchDto): FoodLogDeleteBatchResultDto = error("unused")
    override suspend fun deleteFoodLog(id: Long): Response<Unit> = error("unused")
    override suspend fun putHydration(date: String, body: HydrationUpdateDto): HydrationDto = error("unused")
    override suspend fun postWeightCheckIn(body: WeightCheckInCreateDto): WeightCheckInDto = error("unused")
    override suspend fun getWeightCheckIns(from: String, to: String): WeightCheckInListDto = error("unused")
}

@OptIn(ExperimentalCoroutinesApi::class)
class CopyViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val target = LocalDate.of(2026, 10, 31)
    private val ms = MealSummaryDto(1, 100)

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun stripDaysFor_isSevenDaysBeforeTarget() {
        assertEquals((30 downTo 24).map { LocalDate.of(2026, 10, it) }, stripDaysFor(target))
    }

    @Test
    fun open_mealMode_preselectsMostRecentDayWithThatMeal() = runTest(testDispatcher) {
        val api = FakeCopyLogsApi().apply {
            summary = summaryOf(
                "2026-10-30" to mapOf("breakfast" to ms),
                "2026-10-28" to mapOf("dinner" to ms),
            )
            days["2026-10-28"] = dayOf("dinner" to listOf(log(1, "DINNER")))
        }
        val vm = CopyViewModel(api)
        vm.open(CopyMode.Meal("Dinner"), target)
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(LocalDate.of(2026, 10, 28), s.selectedDay)
        assertEquals(setOf(LocalDate.of(2026, 10, 28)), s.daysWithContent)
        assertEquals("2026-10-01" to "2026-10-30", api.summaryRequests.single())
    }

    @Test
    fun open_dayMode_preselectsMostRecentDayWithAnything() = runTest(testDispatcher) {
        val api = FakeCopyLogsApi().apply {
            summary = summaryOf(
                "2026-10-30" to mapOf("breakfast" to ms),
                "2026-10-28" to mapOf("dinner" to ms),
            )
        }
        val vm = CopyViewModel(api)
        vm.open(CopyMode.Day, target)
        advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 10, 30), vm.state.value.selectedDay)
    }

    @Test
    fun open_preselectedOutsideStrip_setsExtraDay() = runTest(testDispatcher) {
        val api = FakeCopyLogsApi().apply { summary = summaryOf("2026-10-10" to mapOf("lunch" to ms)) }
        val vm = CopyViewModel(api)
        vm.open(CopyMode.Day, target)
        advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 10, 10), vm.state.value.extraDay)
        assertEquals(LocalDate.of(2026, 10, 10), vm.state.value.selectedDay)
    }

    @Test
    fun open_noRecentDays_sourceIsNone() = runTest(testDispatcher) {
        val vm = CopyViewModel(FakeCopyLogsApi())
        vm.open(CopyMode.Day, target)
        advanceUntilIdle()
        assertEquals(SourceDayState.None, vm.state.value.source)
        assertNull(vm.state.value.selectedDay)
        assertEquals(false, vm.state.value.summaryLoading)
    }

    @Test
    fun open_withPastTarget_usesTargetRelativeWindow() = runTest(testDispatcher) {
        val api = FakeCopyLogsApi()
        val vm = CopyViewModel(api)
        vm.open(CopyMode.Day, LocalDate.of(2026, 10, 15))
        advanceUntilIdle()
        assertEquals("2026-09-15" to "2026-10-14", api.summaryRequests.single())
        assertEquals(LocalDate.of(2026, 10, 14), vm.state.value.stripDays.first())
    }

    @Test
    fun candidates_allCheckedByDefault_dayModeOrderedByMeal() = runTest(testDispatcher) {
        val api = FakeCopyLogsApi().apply {
            summary = summaryOf("2026-10-30" to mapOf("snacks" to ms, "breakfast" to ms))
            days["2026-10-30"] = dayOf(
                "snacks" to listOf(log(2, "SNACKS")),
                "breakfast" to listOf(log(1, "BREAKFAST")),
            )
        }
        val vm = CopyViewModel(api)
        vm.open(CopyMode.Day, target)
        advanceUntilIdle()
        val c = (vm.state.value.source as SourceDayState.Loaded).candidates
        assertEquals(listOf("Breakfast", "Snacks"), c.map { it.mealName })
        assertTrue(c.all { it.checked })
        assertEquals(2, vm.state.value.checkedCount)
    }

    private fun vmWithTwoMeals(api: FakeCopyLogsApi): CopyViewModel {
        api.summary = summaryOf("2026-10-30" to mapOf("breakfast" to ms, "lunch" to ms))
        api.days["2026-10-30"] = dayOf(
            "breakfast" to listOf(log(1, "BREAKFAST"), log(2, "BREAKFAST")),
            "lunch" to listOf(log(3, "LUNCH")),
        )
        return CopyViewModel(api)
    }

    private fun CopyViewModel.cands() = (state.value.source as SourceDayState.Loaded).candidates

    @Test
    fun toggleMeal_allChecked_unchecksGroup_elseChecksAll() = runTest(testDispatcher) {
        val vm = vmWithTwoMeals(FakeCopyLogsApi())
        vm.open(CopyMode.Day, target)
        advanceUntilIdle()
        vm.toggleMeal("Breakfast")
        assertEquals(listOf(false, false, true), vm.cands().map { it.checked })
        vm.toggle(1)
        assertEquals(listOf(true, false, true), vm.cands().map { it.checked })
        vm.toggleMeal("Breakfast")
        assertEquals(listOf(true, true, true), vm.cands().map { it.checked })
    }

    @Test
    fun selectDay_resetsEditsAndChecks() = runTest(testDispatcher) {
        val api = FakeCopyLogsApi()
        val vm = vmWithTwoMeals(api)
        vm.open(CopyMode.Day, target)
        advanceUntilIdle()
        vm.toggle(1)
        vm.updatePortion(2, 999, "lingura", 2f)
        vm.selectDay(LocalDate.of(2026, 10, 30))
        advanceUntilIdle()
        val c = vm.cands()
        assertTrue(c.all { it.checked })
        assertEquals(100, c[1].logged.grams)
    }

    @Test
    fun selectDay_whilePreviousLoading_showsLatestDay() = runTest(testDispatcher) {
        val api = FakeCopyLogsApi().apply {
            days["2026-10-29"] = dayOf("lunch" to listOf(log(10, "LUNCH")))
            days["2026-10-28"] = dayOf("lunch" to listOf(log(20, "LUNCH")))
            dayGates["2026-10-29"] = CompletableDeferred()
        }
        val vm = CopyViewModel(api)
        vm.open(CopyMode.Day, target)
        advanceUntilIdle()
        vm.selectDay(LocalDate.of(2026, 10, 29))
        runCurrent()
        vm.selectDay(LocalDate.of(2026, 10, 28))
        advanceUntilIdle()
        api.dayGates["2026-10-29"]!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(20L), vm.cands().map { it.sourceId })
    }

    @Test
    fun copy_sendsOnlyCheckedWithEditedPortions() = runTest(testDispatcher) {
        val ing = listOf(FoodLogIngredientDto("x", 100f, 100f, 1f, 1f, 1f))
        val api = FakeCopyLogsApi().apply {
            summary = summaryOf("2026-10-30" to mapOf("lunch" to ms))
            days["2026-10-30"] = dayOf(
                "lunch" to listOf(
                    log(1, "LUNCH"), log(2, "LUNCH"), log(3, "LUNCH", 100, ing), log(4, "LUNCH", 100, ing),
                ),
            )
        }
        val vm = CopyViewModel(api)
        vm.open(CopyMode.Day, target)
        advanceUntilIdle()
        vm.toggle(1)
        vm.updatePortion(2, 250, "lingura", 2f)
        vm.updateIngredients(4, listOf(FoodIngredient("x", 60f, 100f, 1f, 1f, 1f), FoodIngredient("y", 40.4f, 100f, 1f, 1f, 1f)))
        vm.copy()
        advanceUntilIdle()
        val req = api.copyRequests.single()
        assertEquals("2026-10-31", req.toDate)
        assertEquals(listOf(2L, 3L, 4L), req.items.map { it.sourceId })
        assertEquals(250, req.items[0].grams)
        assertEquals("lingura", req.items[0].unit)
        assertEquals(2f, req.items[0].unitQty)
        assertNull(req.items[1].ingredients)
        assertEquals(2, req.items[2].ingredients!!.size)
        assertEquals(100, req.items[2].grams)
    }

    @Test
    fun copy_success_emitsCopied() = runTest(testDispatcher) {
        val api = apiWithOne()
        val vm = CopyViewModel(api)
        val got = mutableListOf<CopyResult>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.copied.collect { got += it } }
        vm.open(CopyMode.Day, target)
        advanceUntilIdle()
        vm.copy()
        advanceUntilIdle()
        assertEquals(1, got.size)
        assertEquals("2026-10-31", got[0].targetDateKey)
        assertEquals(listOf(99L), got[0].logs.map { it.id })
        assertEquals(false, vm.state.value.copying)
    }

    @Test
    fun open_whileCopyInFlight_doesNotCancelCopy() = runTest(testDispatcher) {
        val api = apiWithOne().apply { copyGate = CompletableDeferred() }
        val vm = CopyViewModel(api)
        val got = mutableListOf<CopyResult>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.copied.collect { got += it } }
        vm.open(CopyMode.Day, target)
        advanceUntilIdle()
        vm.copy()
        runCurrent()
        vm.open(CopyMode.Day, target)
        api.copyGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, got.size)
        assertEquals(listOf(99L), got[0].logs.map { it.id })
    }

    private fun ingredientsApi() = FakeCopyLogsApi().apply {
        summary = summaryOf("2026-10-30" to mapOf("lunch" to ms))
        days["2026-10-30"] = dayOf("lunch" to listOf(log(1, "LUNCH", grams = 300,
            ingredients = listOf(FoodLogIngredientDto("Orez", 200f, 130f, 2.7f, 28f, 0.3f)))))
    }

    @Test
    fun updateIngredients_emptyList_leavesCandidateUnchanged() = runTest(testDispatcher) {
        val vm = CopyViewModel(ingredientsApi())
        vm.open(CopyMode.Day, target)
        advanceUntilIdle()
        val before = vm.cands()[0]
        vm.updateIngredients(before.sourceId, emptyList())
        assertEquals(before, vm.cands()[0])
        assertEquals(false, vm.cands()[0].ingredientsEdited)
    }

    @Test
    fun updateIngredients_recomputesPer100gSoCaloriesMatchIngredients() = runTest(testDispatcher) {
        val vm = CopyViewModel(ingredientsApi())
        vm.open(CopyMode.Day, target)
        advanceUntilIdle()
        val id = vm.cands()[0].sourceId
        val ings = listOf(
            FoodIngredient("Orez", 100f, 130f, 2.7f, 28f, 0.3f),
            FoodIngredient("Pui", 150f, 165f, 31f, 0f, 3.6f),
        )
        vm.updateIngredients(id, ings)
        val c = vm.cands()[0].logged
        assertEquals(250, c.grams)
        assertEquals(ings.sumOf { it.kcal.toDouble() }, c.calories.toDouble(), 1.0)
        assertEquals(ings.sumOf { it.protein.toDouble() }, c.protein.toDouble(), 0.5)
    }

    private fun apiWithOne() = FakeCopyLogsApi().apply {
        summary = summaryOf("2026-10-30" to mapOf("lunch" to ms))
        days["2026-10-30"] = dayOf("lunch" to listOf(log(1, "LUNCH")))
        copyResult = listOf(log(99, "LUNCH"))
    }

    @Test
    fun copy_failure_keepsSelectionAndEmitsError() = runTest(testDispatcher) {
        val api = apiWithOne().apply { failCopy = IOException("offline") }
        val vm = CopyViewModel(api)
        val errs = mutableListOf<FoodViewModel.ToastEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.errors.collect { errs += it } }
        vm.open(CopyMode.Day, target)
        advanceUntilIdle()
        vm.copy()
        advanceUntilIdle()
        assertEquals(listOf(R.string.error_no_internet), errs.map { it.messageRes })
        assertEquals(1, vm.state.value.checkedCount)
        assertEquals(false, vm.state.value.copying)
    }

    @Test
    fun selectDay_whileSummaryLoading_isNotOverwrittenByPreselection() = runTest(testDispatcher) {
        val api = FakeCopyLogsApi().apply {
            summary = summaryOf("2026-10-30" to mapOf("lunch" to ms))
            days["2026-10-30"] = dayOf("lunch" to listOf(log(1, "LUNCH")))
            days["2026-10-27"] = dayOf("lunch" to listOf(log(7, "LUNCH")))
            summaryGate = CompletableDeferred()
        }
        val vm = CopyViewModel(api)
        vm.open(CopyMode.Day, target)
        runCurrent()
        vm.selectDay(LocalDate.of(2026, 10, 27))
        runCurrent()
        api.summaryGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 10, 27), vm.state.value.selectedDay)
        assertEquals(listOf(7L), vm.cands().map { it.sourceId })
        assertEquals(setOf(LocalDate.of(2026, 10, 30)), vm.state.value.daysWithContent)
    }

    @Test
    fun retry_afterSummaryError_recovers() = runTest(testDispatcher) {
        val api = apiWithOne().apply { failSummary = true }
        val vm = CopyViewModel(api)
        vm.open(CopyMode.Day, target)
        advanceUntilIdle()
        assertEquals(R.string.copy_error_load, vm.state.value.summaryError)
        api.failSummary = false
        vm.retry()
        advanceUntilIdle()
        assertNull(vm.state.value.summaryError)
        assertEquals(1, vm.cands().size)
    }

    @Test
    fun copy_httpFailure_emitsSaveErrorWithCode() = runTest(testDispatcher) {
        val body = okhttp3.ResponseBody.create(null, "")
        val api = apiWithOne().apply { failCopy = retrofit2.HttpException(Response.error<Any>(500, body)) }
        val vm = CopyViewModel(api)
        val errs = mutableListOf<FoodViewModel.ToastEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.errors.collect { errs += it } }
        vm.open(CopyMode.Day, target)
        advanceUntilIdle()
        vm.copy()
        advanceUntilIdle()
        assertEquals(listOf(FoodViewModel.ToastEvent(R.string.error_save, 500)), errs)
        assertEquals(1, vm.state.value.checkedCount)
    }

    @Test
    fun candidatesFrom_mealMode_returnsOnlyThatMeal() {
        val day = dayOf("breakfast" to listOf(log(1, "BREAKFAST")), "dinner" to listOf(log(2, "DINNER")))
        assertEquals(listOf(2L), candidatesFrom(day, CopyMode.Meal("Dinner")).map { it.sourceId })
    }
}
