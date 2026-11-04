package com.example.foodtracker

import com.example.foodtracker.api.StreakApi
import com.example.foodtracker.data.StreakSnapshot
import com.example.foodtracker.data.StreakSnapshotStorage
import com.example.foodtracker.model.RecoveryOfferDto
import com.example.foodtracker.model.StreakDayDto
import com.example.foodtracker.model.StreakResponseDto
import com.example.foodtracker.model.StreakTodayDto
import com.example.foodtracker.viewmodel.StreakUiState
import com.example.foodtracker.viewmodel.StreakViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class StreakViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 11, 4)

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun resp(
        current: Int = 5,
        todayLogged: Boolean = false,
        days: List<StreakDayDto> = emptyList(),
        offer: RecoveryOfferDto? = null,
    ) = StreakResponseDto(
        current = current, best = current, totalLoggedDays = current, todayLogged = todayLogged,
        pauseUsedThisWeek = false, pauseUsedOn = null, recoveryAvailableThisMonth = true,
        recoveryUsedOn = null, last14Days = days, nextMilestone = 7, recoveryOffer = offer,
    )

    private class FakeStreakApi : StreakApi {
        var getResponses = ArrayDeque<StreakResponseDto>()
        var getFailure: Throwable? = null
        var recoverResult: Result<StreakResponseDto>? = null
        var declineResult: Result<StreakResponseDto>? = null
        var getCalls = 0
        var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
        override suspend fun getStreak(today: String): StreakResponseDto {
            getCalls++
            getFailure?.let { throw it }
            return if (getResponses.size > 1) getResponses.removeFirst() else getResponses.first()
        }
        override suspend fun recover(body: StreakTodayDto): StreakResponseDto { gate?.await(); return recoverResult!!.getOrThrow() }
        override suspend fun decline(body: StreakTodayDto): StreakResponseDto = declineResult!!.getOrThrow()
    }

    private class FakeSnapshots(var snap: StreakSnapshot? = null) : StreakSnapshotStorage {
        override suspend fun read() = snap
        override suspend fun write(s: StreakSnapshot) { snap = s }
        override suspend fun clear() { snap = null }
    }

    private val offer = RecoveryOfferDto(10, "2026-11-01", "2026-11-02")

    private fun vm(api: FakeStreakApi, snaps: FakeSnapshots = FakeSnapshots()) =
        StreakViewModel(api, snaps, { today })

    @Test fun refreshLoadsAndWritesSnapshot() {
        val api = FakeStreakApi().apply { getResponses += resp(current = 5, todayLogged = true) }
        val snaps = FakeSnapshots(); val vm = vm(api, snaps)
        vm.refresh(); dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value is StreakUiState.Loaded)
        assertEquals(StreakSnapshot(5, today, today), snaps.snap)
    }

    @Test fun snapshotUsesLastLoggedDayWhenTodayNotLogged() {
        val days = listOf(
            StreakDayDto("2026-11-01", "logged"), StreakDayDto("2026-11-02", "missed"),
            StreakDayDto("2026-11-03", "logged"), StreakDayDto("2026-11-04", "today_pending"),
        )
        val api = FakeStreakApi().apply { getResponses += resp(days = days) }
        val snaps = FakeSnapshots(); val vm = vm(api, snaps)
        vm.refresh(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 11, 3), snaps.snap!!.lastLoggedDate)
    }

    @Test fun snapshotLastLoggedNullWhenNoLoggedDay() {
        val api = FakeStreakApi().apply { getResponses += resp(days = listOf(StreakDayDto("2026-11-03", "missed"))) }
        val snaps = FakeSnapshots(); val vm = vm(api, snaps)
        vm.refresh(); dispatcher.scheduler.advanceUntilIdle()
        assertNull(snaps.snap!!.lastLoggedDate)
    }

    @Test fun offlineFallsBackToSnapshot() {
        val d = LocalDate.of(2026, 11, 3)
        val api = FakeStreakApi().apply { getFailure = IOException("down") }
        val vm = vm(api, FakeSnapshots(StreakSnapshot(12, d, d)))
        vm.refresh(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(StreakUiState.Offline(12, todayLogged = false), vm.state.value)
        assertNull(vm.showOffer.value)
    }

    @Test fun offlineWithoutSnapshotStaysLoading() {
        val api = FakeStreakApi().apply { getFailure = IOException("down") }
        val vm = vm(api)
        vm.refresh(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(StreakUiState.Loading, vm.state.value)
    }

    @Test fun milestoneEmittedOnCross() = runTest(dispatcher) {
        val api = FakeStreakApi().apply { getResponses += resp(current = 6); getResponses += resp(current = 7) }
        val vm = vm(api)
        val got = mutableListOf<Int>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.milestones.collect { got += it } }
        vm.refresh(); advanceUntilIdle()
        assertTrue(got.isEmpty())
        vm.refresh(); advanceUntilIdle()
        assertEquals(listOf(7), got)
    }

    @Test fun milestoneEmittedBeforeCollectingIsStillReceived() = runTest(dispatcher) {
        val api = FakeStreakApi().apply { getResponses += resp(current = 6); getResponses += resp(current = 7) }
        val vm = vm(api)
        vm.refresh(); advanceUntilIdle()
        vm.refresh(); advanceUntilIdle()
        val got = mutableListOf<Int>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.milestones.collect { got += it } }
        advanceUntilIdle()
        assertEquals(listOf(7), got)
    }

    @Test fun recoverUpdatesState() {
        val api = FakeStreakApi().apply {
            getResponses += resp(current = 3, offer = offer)
            recoverResult = Result.success(resp(current = 11))
        }
        val vm = vm(api)
        vm.refresh(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(offer, vm.showOffer.value)
        vm.recover(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(11, (vm.state.value as StreakUiState.Loaded).data.current)
        assertNull(vm.showOffer.value)
    }

    @Test fun refreshIsNoOpWhileRecoverInFlight() {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val api = FakeStreakApi().apply {
            getResponses += resp(current = 3, offer = offer)
            recoverResult = Result.success(resp(current = 11))
            this.gate = gate
        }
        val vm = vm(api)
        vm.refresh(); dispatcher.scheduler.advanceUntilIdle()
        val getsBefore = api.getCalls
        vm.recover(); dispatcher.scheduler.advanceUntilIdle()
        vm.refresh(); dispatcher.scheduler.advanceUntilIdle()
        gate.complete(Unit); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(11, (vm.state.value as StreakUiState.Loaded).data.current)
        assertEquals(getsBefore, api.getCalls)
    }

    @Test fun recoverConflictRefreshesAndHidesOffer() {
        val api = FakeStreakApi().apply {
            getResponses += resp(current = 3, offer = offer)
            getResponses += resp(current = 3, offer = null)
            recoverResult = Result.failure(HttpException(Response.error<Any>(409, "".toResponseBody())))
        }
        val vm = vm(api)
        vm.refresh(); dispatcher.scheduler.advanceUntilIdle()
        vm.recover(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, api.getCalls)
        assertNull(vm.showOffer.value)
    }

    @Test fun dismissOfferHidesUntilRefresh() {
        val api = FakeStreakApi().apply { getResponses += resp(offer = offer) }
        val vm = vm(api)
        vm.refresh(); dispatcher.scheduler.advanceUntilIdle()
        vm.dismissOffer(); dispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.showOffer.value)
        vm.refresh(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(offer, vm.showOffer.value)
    }

    @Test fun recoverFromZeroDoesNotEmitMilestone() = runTest(dispatcher) {
        val api = FakeStreakApi().apply {
            getResponses += resp(current = 0, offer = offer)
            recoverResult = Result.success(resp(current = 13))
        }
        val vm = vm(api)
        val got = mutableListOf<Int>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.milestones.collect { got += it } }
        vm.refresh(); advanceUntilIdle()
        vm.recover(); advanceUntilIdle()
        assertEquals(13, (vm.state.value as StreakUiState.Loaded).data.current)
        assertTrue(got.isEmpty())
    }

    @Test fun declineUpdatesStateAndHidesOffer() {
        val api = FakeStreakApi().apply {
            getResponses += resp(current = 0, offer = offer)
            declineResult = Result.success(resp(current = 0, offer = null))
        }
        val vm = vm(api)
        vm.refresh(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(offer, vm.showOffer.value)
        vm.decline(); dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value is StreakUiState.Loaded)
        assertNull((vm.state.value as StreakUiState.Loaded).data.recoveryOffer)
        assertNull(vm.showOffer.value)
    }

    @Test fun offlineStaleSnapshotShowsZero() {
        val old = LocalDate.of(2026, 11, 1)
        val api = FakeStreakApi().apply { getFailure = IOException("down") }
        val vm = vm(api, FakeSnapshots(StreakSnapshot(12, old, old)))
        vm.refresh(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(StreakUiState.Offline(0, todayLogged = false), vm.state.value)
    }

    @Test fun offlineSnapshotFromTodayIsPlausible() {
        val api = FakeStreakApi().apply { getFailure = IOException("down") }
        val vm = vm(api, FakeSnapshots(StreakSnapshot(12, today, today)))
        vm.refresh(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(StreakUiState.Offline(12, todayLogged = true), vm.state.value)
    }

    @Test fun resetClearsSnapshot() = runTest(dispatcher) {
        val api = FakeStreakApi().apply { getResponses += resp() }
        val snaps = FakeSnapshots(); val vm = vm(api, snaps)
        vm.refresh(); advanceUntilIdle()
        vm.resetUserState(); advanceUntilIdle()
        assertEquals(StreakUiState.Loading, vm.state.value)
        assertNull(snaps.read())
    }
}
