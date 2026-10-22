package com.example.foodtracker

import com.example.foodtracker.api.PortionsApi
import com.example.foodtracker.model.GramPresetsDto
import com.example.foodtracker.model.PortionUnitDto
import com.example.foodtracker.model.PortionUnitInDto
import com.example.foodtracker.model.PortionsDto
import com.example.foodtracker.viewmodel.PortionsState
import com.example.foodtracker.viewmodel.PortionsViewModel
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

private class FakePortionsApi : PortionsApi {
    val byBarcode = mutableMapOf<String, PortionsDto>()
    val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
    var failGet = false
    var failWrites = false
    val puts = mutableListOf<Pair<String, PortionUnitInDto>>()
    val deletedUnits = mutableListOf<Pair<String, String>>()
    val presetPuts = mutableListOf<Pair<String, List<Int>>>()
    val presetDeletes = mutableListOf<String>()

    override suspend fun getPortions(barcode: String): PortionsDto {
        gates[barcode]?.await()
        if (failGet) throw IOException("offline")
        return byBarcode[barcode] ?: PortionsDto()
    }
    override suspend fun putUnit(barcode: String, body: PortionUnitInDto): PortionUnitInDto {
        if (failWrites) throw IOException("offline")
        puts += barcode to body
        val cur = byBarcode[barcode] ?: PortionsDto()
        byBarcode[barcode] = cur.copy(units = cur.units + PortionUnitDto(body.unit, body.grams, "user"))
        return body
    }
    override suspend fun deleteUnit(barcode: String, unit: String): Response<Unit> {
        if (failWrites) throw IOException("offline")
        deletedUnits += barcode to unit
        return Response.success(null)
    }
    override suspend fun putPresets(barcode: String, body: GramPresetsDto): GramPresetsDto {
        if (failWrites) throw IOException("offline")
        presetPuts += barcode to body.grams
        return body
    }
    override suspend fun deletePresets(barcode: String): Response<Unit> {
        if (failWrites) throw IOException("offline")
        presetDeletes += barcode
        return Response.success(null)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PortionsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun load_emitsLoadingThenLoaded() = runTest(testDispatcher) {
        val api = FakePortionsApi().apply {
            byBarcode["1"] = PortionsDto(gramPresets = listOf(50, 100))
        }
        val vm = PortionsViewModel(api)
        assertEquals(PortionsState.Idle, vm.state.value)
        vm.load("1")
        assertEquals(PortionsState.Loading, vm.state.value)
        advanceUntilIdle()

        val loaded = vm.state.value as PortionsState.Loaded
        assertEquals("1", loaded.barcode)
        assertEquals(listOf(50, 100), loaded.portions.gramPresets)
    }

    @Test
    fun load_failureEmitsFailed() = runTest(testDispatcher) {
        val api = FakePortionsApi().apply { failGet = true }
        val vm = PortionsViewModel(api)
        vm.load("1")
        advanceUntilIdle()
        assertEquals(PortionsState.Failed, vm.state.value)
    }

    @Test
    fun staleLoad_doesNotOverwriteNewerBarcode() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        val api = FakePortionsApi().apply {
            gates["a"] = gate
            byBarcode["a"] = PortionsDto(gramPresets = listOf(1))
            byBarcode["b"] = PortionsDto(gramPresets = listOf(2))
        }
        val vm = PortionsViewModel(api)
        vm.load("a")
        advanceUntilIdle()
        vm.load("b")
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        val loaded = vm.state.value as PortionsState.Loaded
        assertEquals("b", loaded.barcode)
        assertEquals(listOf(2), loaded.portions.gramPresets)
    }

    @Test
    fun saveUnit_putsThenReloadsWithoutLoading() = runTest(testDispatcher) {
        val api = FakePortionsApi()
        val vm = PortionsViewModel(api)
        vm.load("1")
        advanceUntilIdle()

        val seen = mutableListOf<PortionsState>()
        backgroundScope.launch { vm.state.collect { seen += it } }
        runCurrent()
        vm.saveUnit("1", "my bowl", 250f)
        advanceUntilIdle()
        runCurrent()

        assertEquals(listOf("1" to PortionUnitInDto("my bowl", 250f)), api.puts)
        assertTrue(seen.none { it == PortionsState.Loading })
        val loaded = vm.state.value as PortionsState.Loaded
        assertEquals(listOf("my bowl"), loaded.portions.units.map { it.unit })
    }

    @Test
    fun saveUnit_failureKeepsStateAndEmitsToast() = runTest(testDispatcher) {
        val api = FakePortionsApi()
        val vm = PortionsViewModel(api)
        val toasts = mutableListOf<Int>()
        backgroundScope.launch { vm.toastEvents.collect { toasts += it } }
        vm.load("1")
        advanceUntilIdle()
        val before = vm.state.value
        api.failWrites = true

        vm.saveUnit("1", "x", 10f)
        advanceUntilIdle()
        runCurrent()

        assertEquals(before, vm.state.value)
        assertEquals(listOf(R.string.error_no_internet), toasts)
    }

    @Test
    fun resetUserState_returnsToIdle() = runTest(testDispatcher) {
        val vm = PortionsViewModel(FakePortionsApi())
        vm.load("1")
        advanceUntilIdle()
        vm.resetUserState()
        assertEquals(PortionsState.Idle, vm.state.value)
    }
}
