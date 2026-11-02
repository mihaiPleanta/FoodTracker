package com.example.foodtracker

import com.example.foodtracker.api.CustomFoodsApi
import com.example.foodtracker.api.PortionsApi
import com.example.foodtracker.model.CustomFoodDto
import com.example.foodtracker.model.CustomFoodInDto
import com.example.foodtracker.model.GramPresetsDto
import com.example.foodtracker.model.PortionUnitInDto
import com.example.foodtracker.model.PortionsDto
import com.example.foodtracker.model.ServingDto
import com.example.foodtracker.ui.customfood.FormField
import com.example.foodtracker.util.label.ParsedLabel
import com.example.foodtracker.viewmodel.CustomFoodViewModel
import com.example.foodtracker.viewmodel.MyFoodsState
import com.example.foodtracker.viewmodel.SaveState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

private fun dto(
    id: Int = 1, barcode: String? = null, serving: ServingDto? = null,
) = CustomFoodDto(
    id = id, key = "custom-$id", name = "Tort", barcode = barcode,
    kcal100g = 300f, protein100g = 5f, carbs100g = 40f, fat100g = 12f, serving = serving,
)

private class FakeCustomFoodsApi : CustomFoodsApi {
    var items = mutableListOf<CustomFoodDto>()
    var createCalls = 0
    var updateCalls = 0
    val deleted = mutableListOf<Int>()
    var createError: Exception? = null
    var nextId = 7

    override suspend fun list(): List<CustomFoodDto> = items.toList()
    override suspend fun create(body: CustomFoodInDto): CustomFoodDto {
        createCalls++
        createError?.let { throw it }
        return CustomFoodDto(
            id = nextId, key = "custom-$nextId", name = body.name, brand = body.brand, barcode = body.barcode,
            kcal100g = body.kcal100g, protein100g = body.protein100g, carbs100g = body.carbs100g,
            fat100g = body.fat100g, serving = body.serving,
        ).also { items += it; nextId++ }
    }
    override suspend fun update(id: Int, body: CustomFoodInDto): CustomFoodDto {
        updateCalls++
        return CustomFoodDto(
            id = id, key = "custom-$id", name = body.name, brand = body.brand, barcode = body.barcode,
            kcal100g = body.kcal100g, protein100g = body.protein100g, carbs100g = body.carbs100g,
            fat100g = body.fat100g, serving = body.serving,
        )
    }
    override suspend fun delete(id: Int): Response<Unit> {
        deleted += id
        items.removeAll { it.id == id }
        return Response.success(null)
    }
}

private class FakeCfPortionsApi : PortionsApi {
    var failPut = false
    val puts = mutableListOf<Triple<String, String, Float>>()
    val deletes = mutableListOf<Pair<String, String>>()
    override suspend fun getPortions(barcode: String): PortionsDto = throw UnsupportedOperationException()
    override suspend fun putUnit(barcode: String, body: PortionUnitInDto): PortionUnitInDto {
        if (failPut) throw java.io.IOException("offline")
        puts += Triple(barcode, body.unit, body.grams)
        return body
    }
    override suspend fun deleteUnit(barcode: String, unit: String): Response<Unit> {
        deletes += barcode to unit
        return Response.success(null)
    }
    override suspend fun putPresets(barcode: String, body: GramPresetsDto): GramPresetsDto = body
    override suspend fun deletePresets(barcode: String): Response<Unit> = Response.success(null)
}

@OptIn(ExperimentalCoroutinesApi::class)
class CustomFoodViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var foods: FakeCustomFoodsApi
    private lateinit var portions: FakeCfPortionsApi
    private val invalidated = mutableListOf<List<String>>()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        foods = FakeCustomFoodsApi()
        portions = FakeCfPortionsApi()
        invalidated.clear()
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = CustomFoodViewModel(
        api = foods, portionsApi = portions, defaultServingUnit = "porție",
        invalidate = { invalidated += it },
    )

    private fun CustomFoodViewModel.fillValid() {
        onField(FormField.NAME, "Tort")
        onField(FormField.KCAL, "300")
        onField(FormField.PROTEIN, "5")
        onField(FormField.CARBS, "40")
        onField(FormField.FAT, "12")
    }

    private fun httpError(code: Int, body: String = "{}") =
        HttpException(Response.error<Any>(code, body.toResponseBody("application/json".toMediaType())))

    @Test fun save_new_createsAndEmitsCreatedForMeal() = runTest(dispatcher) {
        val vm = vm()
        vm.startNew("12345678", "Tort", forMeal = true)
        vm.fillValid()
        vm.save()
        advanceUntilIdle()
        val saved = vm.saveState.value as SaveState.Saved
        assertEquals("custom-7", saved.food.barcode)
        assertEquals("custom-7", vm.createdForMeal.value?.barcode)
        vm.consumeCreated()
        assertNull(vm.createdForMeal.value)
    }

    @Test fun save_new_notForMeal_noCreatedEvent() = runTest(dispatcher) {
        val vm = vm()
        vm.startNew(null, null, forMeal = false)
        vm.fillValid()
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.saveState.value is SaveState.Saved)
        assertNull(vm.createdForMeal.value)
    }

    @Test fun save_invalidForm_doesNotCallApi() = runTest(dispatcher) {
        val vm = vm()
        vm.startNew(null, null, forMeal = false)
        assertEquals(false, vm.showErrors.value)
        vm.save()
        advanceUntilIdle()
        assertEquals(0, foods.createCalls)
        assertEquals(true, vm.showErrors.value)
        assertTrue(vm.saveState.value is SaveState.Idle)
    }

    @Test fun save_409_emitsBarcodeTaken() = runTest(dispatcher) {
        foods.createError = httpError(
            409, """{"detail":{"code":"barcode_taken","existing_id":42}}"""
        )
        val vm = vm()
        vm.startNew("12345678", null, forMeal = false)
        vm.fillValid()
        vm.save()
        advanceUntilIdle()
        assertEquals(SaveState.BarcodeTaken(42), vm.saveState.value)
    }

    @Test fun save_noInternet_emitsError() = runTest(dispatcher) {
        foods.createError = java.io.IOException("offline")
        val vm = vm()
        vm.startNew(null, null, forMeal = false)
        vm.fillValid()
        vm.save()
        advanceUntilIdle()
        assertEquals(SaveState.Error(R.string.error_no_internet), vm.saveState.value)
    }

    @Test fun save_invalidatesCacheWithKeyAndBarcodes() = runTest(dispatcher) {
        val vm = vm()
        vm.startNew("12345678", null, forMeal = false)
        vm.fillValid()
        vm.save()
        advanceUntilIdle()
        assertEquals(listOf(listOf("custom-7", "12345678")), invalidated)
    }

    private fun CustomFoodViewModel.startEditOf(item: CustomFoodDto): CustomFoodViewModel {
        foods.items = mutableListOf(item)
        loadMyFoods()
        return this
    }

    @Test fun edit_servingChanged_deletesOldAndPutsNewUnit() = runTest(dispatcher) {
        val vm = vm().startEditOf(dto(serving = ServingDto("felie", 30f)))
        advanceUntilIdle()
        vm.startEdit(1)
        advanceUntilIdle()
        vm.onField(FormField.SERVING_UNIT, "bucată")
        vm.onField(FormField.SERVING_GRAMS, "50")
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.saveState.value is SaveState.Saved)
        assertEquals(listOf("custom-1" to "felie"), portions.deletes)
        assertEquals(listOf(Triple("custom-1", "bucată", 50f)), portions.puts)
    }

    @Test fun edit_servingCleared_deletesUnit() = runTest(dispatcher) {
        val vm = vm().startEditOf(dto(serving = ServingDto("felie", 30f)))
        advanceUntilIdle()
        vm.startEdit(1)
        advanceUntilIdle()
        vm.onField(FormField.SERVING_UNIT, "")
        vm.onField(FormField.SERVING_GRAMS, "")
        vm.save()
        advanceUntilIdle()
        assertEquals(listOf("custom-1" to "felie"), portions.deletes)
        assertTrue(portions.puts.isEmpty())
    }

    @Test fun edit_barcodeChanged_invalidatesOldAndNew() = runTest(dispatcher) {
        val vm = vm().startEditOf(dto(barcode = "11111111"))
        advanceUntilIdle()
        vm.startEdit(1)
        advanceUntilIdle()
        vm.onField(FormField.BARCODE, "22222222")
        vm.save()
        advanceUntilIdle()
        assertEquals(listOf(listOf("custom-1", "11111111", "22222222")), invalidated)
    }

    @Test fun onLabelRecognized_empty_setsOcrMessageAndKeepsForm() = runTest(dispatcher) {
        val vm = vm()
        vm.startNew(null, "Tort", forMeal = false)
        vm.onLabelRecognized("content://x", ParsedLabel(null, null, null, null, null, null))
        assertEquals(R.string.custom_food_ocr_nothing, vm.ocrMessage.value)
        assertEquals("content://x", vm.labelImage.value)
        assertEquals("Tort", vm.form.value.values[FormField.NAME])
        assertNull(vm.form.value.values[FormField.KCAL])
    }

    @Test fun onLabelRecognized_values_fillsForm() = runTest(dispatcher) {
        val vm = vm()
        vm.startNew(null, null, forMeal = false)
        vm.onLabelRecognized("content://x", ParsedLabel(250f, 5f, 30f, 10f, 30f, null))
        assertNull(vm.ocrMessage.value)
        assertEquals("250", vm.form.value.values[FormField.KCAL])
        assertEquals("porție", vm.form.value.values[FormField.SERVING_UNIT])
    }

    @Test fun delete_reloadsListAndInvalidates() = runTest(dispatcher) {
        val vm = vm().startEditOf(dto(barcode = "11111111"))
        advanceUntilIdle()
        vm.delete(1)
        advanceUntilIdle()
        assertEquals(listOf(1), foods.deleted)
        assertEquals(listOf(listOf("custom-1", "11111111")), invalidated)
        assertEquals(MyFoodsState.Loaded(emptyList()), vm.myFoods.value)
    }

    @Test fun edit_unitSyncFails_stillInvalidatesAndRefreshes() = runTest(dispatcher) {
        val vm = vm().startEditOf(dto(barcode = "11111111", serving = ServingDto("felie", 30f)))
        advanceUntilIdle()
        vm.startEdit(1)
        advanceUntilIdle()
        portions.failPut = true
        foods.items = mutableListOf(dto(id = 1, barcode = "11111111").copy(name = "Nou"))
        vm.onField(FormField.SERVING_UNIT, "bucată")
        vm.onField(FormField.SERVING_GRAMS, "50")
        vm.save()
        advanceUntilIdle()
        assertEquals(SaveState.Error(R.string.error_no_internet), vm.saveState.value)
        assertEquals(listOf(listOf("custom-1", "11111111")), invalidated)
        assertEquals("Nou", (vm.myFoods.value as MyFoodsState.Loaded).items.single().name)
    }

    @Test fun save_new_invalidateThrows_stillSaved() = runTest(dispatcher) {
        val vm = CustomFoodViewModel(
            api = foods, portionsApi = portions, defaultServingUnit = "porție",
            invalidate = { throw IllegalStateException("db") },
        )
        vm.startNew(null, null, forMeal = false)
        vm.fillValid()
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.saveState.value is SaveState.Saved)
        assertEquals(1, foods.createCalls)
    }

    @Test fun startEdit_unknownId_neverCreates() = runTest(dispatcher) {
        val vm = vm()
        vm.startEdit(99)
        vm.fillValid()
        vm.save()
        advanceUntilIdle()
        vm.save()
        assertEquals(0, foods.createCalls)
        assertEquals(0, foods.updateCalls)
        assertEquals(SaveState.Error(R.string.error_generic), vm.saveState.value)
    }

    @Test fun startEdit_listNotLoaded_fetchesThenEdits() = runTest(dispatcher) {
        foods.items = mutableListOf(dto(id = 3))
        val vm = vm()
        vm.startEdit(3)
        advanceUntilIdle()
        assertEquals("Tort", vm.form.value.values[FormField.NAME])
        vm.save()
        advanceUntilIdle()
        assertEquals(1, foods.updateCalls)
        assertEquals(0, foods.createCalls)
    }

    @Test fun resetUserState_clearsEverything() = runTest(dispatcher) {
        val vm = vm().startEditOf(dto())
        advanceUntilIdle()
        vm.startNew("12345678", "x", forMeal = true)
        vm.fillValid()
        vm.onLabelRecognized("content://x", ParsedLabel(null, null, null, null, null, null))
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.saveState.value is SaveState.Saved)
        assertNotNull(vm.createdForMeal.value)
        assertNotNull(vm.labelImage.value)
        assertNotNull(vm.ocrMessage.value)
        assertTrue(vm.showErrors.value)
        vm.resetUserState()
        advanceUntilIdle()
        assertEquals(MyFoodsState.Loading, vm.myFoods.value)
        assertTrue(vm.form.value.values.isEmpty())
        assertNull(vm.labelImage.value)
        assertNull(vm.ocrMessage.value)
        assertNull(vm.createdForMeal.value)
        assertEquals(SaveState.Idle, vm.saveState.value)
        assertEquals(false, vm.showErrors.value)
    }
}
