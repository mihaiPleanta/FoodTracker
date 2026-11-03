package com.example.foodtracker

import com.example.foodtracker.api.DaysApi
import com.example.foodtracker.model.DayStatusResponseDto
import com.example.foodtracker.model.DayStatusUpdateDto
import com.example.foodtracker.model.StatsDayDto
import com.example.foodtracker.model.StatsDaysResponseDto
import com.example.foodtracker.model.StatsGoalsDto
import com.example.foodtracker.viewmodel.PeriodUiState
import com.example.foodtracker.viewmodel.StatsMode
import com.example.foodtracker.viewmodel.StatsViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 11, 4) // Wednesday

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class FakeDaysApi : DaysApi {
        val calls = mutableListOf<Pair<String, String>>()
        var failWith: Throwable? = null
        var gate: ((String) -> CompletableDeferred<Unit>?)? = null
        var kcal = 2000

        override suspend fun getStatsDays(from: String, to: String): StatsDaysResponseDto {
            calls += from to to
            gate?.invoke(from)?.await()
            failWith?.let { throw it }
            val d = StatsDayDto(
                date = from, kcal = kcal, proteinG = 0f, carbsG = 0f, fatG = 0f, hasFood = true,
                status = "complete", goals = StatsGoalsDto(2000, 0, 0, 0, 2000),
            )
            return StatsDaysResponseDto(days = listOf(d))
        }
        override suspend fun putStatus(date: String, body: DayStatusUpdateDto): DayStatusResponseDto = error("unused")
        override suspend fun deleteStatus(date: String): Response<Unit> = error("unused")
    }

    private fun vm(api: FakeDaysApi) = StatsViewModel(api, { today })

    @Test fun setMode_week_fetchesCurrentAndPrevious() {
        val api = FakeDaysApi(); val vm = vm(api)
        vm.setMode(StatsMode.WEEK, today); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(setOf("2026-11-02" to "2026-11-08", "2026-10-26" to "2026-11-01"), api.calls.toSet())
        val s = vm.state.value as PeriodUiState.Loaded
        assertEquals(LocalDate.of(2026, 11, 2), s.period.start)
        assertEquals("2026-11-02", s.raw.days.single().date)
    }

    @Test fun previous_movesOneWeekBack() {
        val api = FakeDaysApi(); val vm = vm(api)
        vm.setMode(StatsMode.WEEK, today); dispatcher.scheduler.advanceUntilIdle()
        vm.previous(); dispatcher.scheduler.advanceUntilIdle()
        val s = vm.state.value as PeriodUiState.Loaded
        assertEquals(LocalDate.of(2026, 10, 26), s.period.start)
        assertTrue(s.canGoNext)
    }

    @Test fun next_disabledOnCurrentPeriod() {
        val api = FakeDaysApi(); val vm = vm(api)
        vm.setMode(StatsMode.WEEK, today); dispatcher.scheduler.advanceUntilIdle()
        assertFalse((vm.state.value as PeriodUiState.Loaded).canGoNext)
        vm.next(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 11, 2), (vm.state.value as PeriodUiState.Loaded).period.start)
    }

    @Test fun toleranceChange_recomputesWithoutRefetch() {
        val api = FakeDaysApi(); api.kcal = 2300; val vm = vm(api)
        vm.setMode(StatsMode.WEEK, today); dispatcher.scheduler.advanceUntilIdle()
        val before = (vm.state.value as PeriodUiState.Loaded).summary.inTargetDays
        val n = api.calls.size
        vm.setTolerance(15); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(n, api.calls.size)
        assertEquals(0, before)
        assertEquals(1, (vm.state.value as PeriodUiState.Loaded).summary.inTargetDays)
    }

    @Test fun cachedPeriod_notRefetched() {
        val api = FakeDaysApi(); val vm = vm(api)
        vm.setMode(StatsMode.WEEK, today); dispatcher.scheduler.advanceUntilIdle()
        vm.previous(); dispatcher.scheduler.advanceUntilIdle()
        val n = api.calls.size
        vm.next(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(n, api.calls.size)
        assertEquals(LocalDate.of(2026, 11, 2), (vm.state.value as PeriodUiState.Loaded).period.start)
    }

    @Test fun invalidate_refetches() {
        val api = FakeDaysApi(); val vm = vm(api)
        vm.setMode(StatsMode.WEEK, today); dispatcher.scheduler.advanceUntilIdle()
        val n = api.calls.size
        vm.invalidate(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(n * 2, api.calls.size)
    }

    @Test fun ioError_showsNoInternet() {
        val api = FakeDaysApi(); api.failWith = IOException("x"); val vm = vm(api)
        vm.setMode(StatsMode.MONTH, today); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(R.string.error_no_internet, (vm.state.value as PeriodUiState.Error).message)
        api.failWith = IllegalStateException("x"); vm.retry(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(R.string.error_load_stats, (vm.state.value as PeriodUiState.Error).message)
    }

    @Test fun fastNavigation_lastPeriodWins() {
        val api = FakeDaysApi()
        val slow = CompletableDeferred<Unit>()
        api.gate = { from -> if (from == "2026-10-26") slow else null } // current of week-1 = slow
        val vm = vm(api)
        vm.setMode(StatsMode.WEEK, today); dispatcher.scheduler.advanceUntilIdle()
        // previous() targets 10-26 as current (blocked); navigate again before it finishes
        vm.previous(); dispatcher.scheduler.advanceUntilIdle()
        vm.previous(); dispatcher.scheduler.advanceUntilIdle()
        slow.complete(Unit); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 10, 19), (vm.state.value as PeriodUiState.Loaded).period.start)
    }

    @Test fun resetUserState_clearsCacheAndReturnsToDay() {
        val api = FakeDaysApi(); val vm = vm(api)
        vm.setMode(StatsMode.WEEK, today); dispatcher.scheduler.advanceUntilIdle()
        val n = api.calls.size
        vm.resetUserState()
        assertEquals(StatsMode.DAY, vm.mode.value)
        assertEquals(PeriodUiState.Loading, vm.state.value)
        vm.setMode(StatsMode.WEEK, today); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(n * 2, api.calls.size)
    }

    @Test fun setMode_fromDay_refetches() {
        val api = FakeDaysApi(); val vm = vm(api)
        vm.setMode(StatsMode.WEEK, today); dispatcher.scheduler.advanceUntilIdle()
        val n = api.calls.size
        vm.setMode(StatsMode.MONTH, today); dispatcher.scheduler.advanceUntilIdle() // no DAY in between
        vm.setMode(StatsMode.DAY, today)
        vm.setMode(StatsMode.WEEK, today); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(n + 2 + n, api.calls.size)
    }
}
