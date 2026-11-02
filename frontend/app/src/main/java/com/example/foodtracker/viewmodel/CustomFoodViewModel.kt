package com.example.foodtracker.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.foodtracker.R
import com.example.foodtracker.api.CustomFoodsApi
import com.example.foodtracker.api.PortionsApi
import com.example.foodtracker.api.RetrofitInstance
import com.example.foodtracker.model.CustomFoodDto
import com.example.foodtracker.model.FoodItem
import com.example.foodtracker.model.PortionUnitInDto
import com.example.foodtracker.model.parseBarcodeTaken
import com.example.foodtracker.ui.customfood.CustomFoodForm
import com.example.foodtracker.ui.customfood.FormField
import com.example.foodtracker.util.label.ParsedLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

sealed interface MyFoodsState {
    data object Loading : MyFoodsState
    data class Loaded(val items: List<CustomFoodDto>) : MyFoodsState
    data class Error(@StringRes val msg: Int) : MyFoodsState
}

sealed interface SaveState {
    data object Idle : SaveState
    data object Saving : SaveState
    data class Saved(val food: FoodItem) : SaveState
    data class BarcodeTaken(val existingId: Int) : SaveState
    data class Error(@StringRes val msg: Int) : SaveState
}

/**
 * User-created foods: list, create/edit/delete, label-photo prefill. Activity-scoped,
 * so logout must call [resetUserState].
 */
class CustomFoodViewModel(
    private val api: CustomFoodsApi = RetrofitInstance.customFoodsApi,
    private val portionsApi: PortionsApi = RetrofitInstance.portionsApi,
    private val defaultServingUnit: String = "",
    private val invalidate: suspend (List<String>) -> Unit = {},
) : ViewModel() {

    private val _myFoods = MutableStateFlow<MyFoodsState>(MyFoodsState.Loading)
    val myFoods: StateFlow<MyFoodsState> = _myFoods.asStateFlow()

    private val _form = MutableStateFlow(CustomFoodForm())
    val form: StateFlow<CustomFoodForm> = _form.asStateFlow()

    private val _labelImage = MutableStateFlow<String?>(null)
    val labelImage: StateFlow<String?> = _labelImage.asStateFlow()

    private val _ocrMessage = MutableStateFlow<Int?>(null)
    val ocrMessage: StateFlow<Int?> = _ocrMessage.asStateFlow()

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    /** True after the first save attempt; the screen shows field errors only from then on. */
    private val _showErrors = MutableStateFlow(false)
    val showErrors: StateFlow<Boolean> = _showErrors.asStateFlow()

    private val _createdForMeal = MutableStateFlow<FoodItem?>(null)
    val createdForMeal: StateFlow<FoodItem?> = _createdForMeal.asStateFlow()

    private var editing: CustomFoodDto? = null
    private var editId: Int? = null
    private var editJob: Job? = null
    private var forMeal = false
    private var listJob: Job? = null
    private var saveJob: Job? = null

    fun loadMyFoods() {
        listJob?.cancel()
        listJob = viewModelScope.launch { fetchList() }
    }

    fun startNew(barcode: String?, name: String?, forMeal: Boolean) {
        clearFlow()
        this.forMeal = forMeal
        _form.value = CustomFoodForm.prefilled(barcode, name)
    }

    /** Loads the list first when needed; an unknown id puts the flow in an Error state (save is a no-op). */
    fun startEdit(id: Int) {
        clearFlow()
        editId = id
        editJob = viewModelScope.launch {
            if (findLoaded(id) == null) fetchList()
            val item = findLoaded(id)
            if (item == null) {
                _saveState.value = SaveState.Error(R.string.error_generic)
            } else {
                editing = item
                _form.value = CustomFoodForm.fromDto(item)
            }
        }
    }

    private fun findLoaded(id: Int): CustomFoodDto? =
        (_myFoods.value as? MyFoodsState.Loaded)?.items?.firstOrNull { it.id == id }

    fun onField(field: FormField, text: String) {
        _form.value = _form.value.edit(field, text)
    }

    fun onLabelRecognized(imageUri: String, parsed: ParsedLabel) {
        _labelImage.value = imageUri
        if (parsed.isEmpty) {
            _ocrMessage.value = R.string.custom_food_ocr_nothing
        } else {
            _ocrMessage.value = null
            _form.value = _form.value.applyParsed(parsed, defaultServingUnit)
        }
    }

    fun skipPhoto() {
        _labelImage.value = null
        _ocrMessage.value = null
    }

    fun save() {
        if (_saveState.value is SaveState.Saving) return
        if (editId != null && editing == null) return
        _showErrors.value = true
        val body = _form.value.toInDto() ?: return
        val old = editing
        _saveState.value = SaveState.Saving
        saveJob = viewModelScope.launch {
            try {
                if (old == null) {
                    val created = api.create(body)
                    safeInvalidate(listOfNotNull(created.key, created.barcode))
                    val food = created.toFoodItem()
                    if (forMeal) _createdForMeal.value = food
                    _saveState.value = SaveState.Saved(food)
                } else {
                    val updated = api.update(old.id, body)
                    try {
                        syncServingUnit(updated.key, old.serving, updated.serving)
                    } finally {
                        // The server already has the new data: refresh local state even if the unit sync failed.
                        safeInvalidate(listOfNotNull(updated.key, old.barcode, updated.barcode).distinct())
                        fetchList()
                    }
                    _saveState.value = SaveState.Saved(updated.toFoodItem())
                }
                if (old == null) fetchList()
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                val taken = parseBarcodeTaken(e)
                _saveState.value =
                    if (taken != null) SaveState.BarcodeTaken(taken) else SaveState.Error(R.string.error_generic)
            } catch (e: IOException) {
                _saveState.value = SaveState.Error(R.string.error_no_internet)
            } catch (e: Exception) {
                _saveState.value = SaveState.Error(R.string.error_generic)
            }
        }
    }

    fun delete(id: Int) {
        viewModelScope.launch {
            try {
                val item = (_myFoods.value as? MyFoodsState.Loaded)?.items?.firstOrNull { it.id == id }
                val response = api.delete(id)
                if (!response.isSuccessful) throw HttpException(response)
                safeInvalidate(listOfNotNull(item?.key ?: "custom-$id", item?.barcode))
                fetchList()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _myFoods.value = MyFoodsState.Error(
                    if (e is IOException) R.string.error_no_internet else R.string.error_generic
                )
            }
        }
    }

    fun consumeCreated() {
        _createdForMeal.value = null
    }

    /** Leaves the form flow (screen closed); keeps the loaded list. */
    fun clearFlow() {
        saveJob?.cancel()
        editJob?.cancel()
        editing = null
        editId = null
        forMeal = false
        _form.value = CustomFoodForm()
        _labelImage.value = null
        _ocrMessage.value = null
        _saveState.value = SaveState.Idle
        _showErrors.value = false
    }

    fun resetUserState() {
        listJob?.cancel()
        clearFlow()
        _createdForMeal.value = null
        _myFoods.value = MyFoodsState.Loading
    }

    private suspend fun syncServingUnit(key: String, old: com.example.foodtracker.model.ServingDto?, new: com.example.foodtracker.model.ServingDto?) {
        if (old != null && old.unit != new?.unit) {
            val r = portionsApi.deleteUnit(key, old.unit)
            if (!r.isSuccessful) throw HttpException(r)
        }
        if (new != null && (old?.unit != new.unit || old.grams != new.grams)) {
            portionsApi.putUnit(key, PortionUnitInDto(new.unit, new.grams))
        }
    }

    /** A local cache error must not turn a successful server write into a failed save. */
    private suspend fun safeInvalidate(keys: List<String>) {
        try {
            invalidate(keys)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private suspend fun fetchList() {
        _myFoods.value = try {
            MyFoodsState.Loaded(api.list())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MyFoodsState.Error(if (e is IOException) R.string.error_no_internet else R.string.error_generic)
        }
    }
}

class CustomFoodViewModelFactory(
    private val defaultServingUnit: String,
    private val invalidate: suspend (List<String>) -> Unit,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(CustomFoodViewModel::class.java)) {
            "CustomFoodViewModelFactory only creates CustomFoodViewModel, got ${modelClass.name}"
        }
        return CustomFoodViewModel(
            defaultServingUnit = defaultServingUnit,
            invalidate = invalidate,
        ) as T
    }
}
