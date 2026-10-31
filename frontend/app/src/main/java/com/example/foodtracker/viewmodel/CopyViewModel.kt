package com.example.foodtracker.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.foodtracker.R
import com.example.foodtracker.api.LogsApi
import com.example.foodtracker.api.RetrofitInstance
import com.example.foodtracker.model.DayResponseDto
import com.example.foodtracker.model.FoodIngredient
import com.example.foodtracker.model.FoodLogCopyItemDto
import com.example.foodtracker.model.FoodLogCopyRequestDto
import com.example.foodtracker.model.FoodLogDto
import com.example.foodtracker.model.FoodLogSummaryDto
import com.example.foodtracker.model.LoggedFood
import com.example.foodtracker.model.MEAL_ORDER
import com.example.foodtracker.model.mealKeyToBackend
import com.example.foodtracker.model.mealNameFromBackend
import com.example.foodtracker.model.toDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import kotlin.math.roundToInt

sealed interface CopyMode {
    data class Meal(val mealName: String) : CopyMode
    data object Day : CopyMode
}

data class CopyCandidate(
    val sourceId: Long,
    val mealName: String,
    val logged: LoggedFood,
    val checked: Boolean = true,
    val ingredientsEdited: Boolean = false,
)

sealed interface SourceDayState {
    data object None : SourceDayState
    data object Loading : SourceDayState
    data class Loaded(val candidates: List<CopyCandidate>) : SourceDayState
    data class Error(@StringRes val messageRes: Int) : SourceDayState
}

data class CopyUiState(
    val mode: CopyMode = CopyMode.Day,
    val targetDate: LocalDate = LocalDate.now(),
    val stripDays: List<LocalDate> = emptyList(),
    val daysWithContent: Set<LocalDate> = emptySet(),
    val extraDay: LocalDate? = null,
    val selectedDay: LocalDate? = null,
    val summaryLoading: Boolean = false,
    @StringRes val summaryError: Int? = null,
    val source: SourceDayState = SourceDayState.None,
    val copying: Boolean = false,
) {
    val checkedCount: Int
        get() = (source as? SourceDayState.Loaded)?.candidates?.count { it.checked } ?: 0
}

data class CopyResult(val targetDateKey: String, val logs: List<FoodLogDto>)

const val COPY_STRIP_DAYS = 7
const val COPY_PRESELECT_WINDOW_DAYS = 30L

/** The 7 days before [target], most recent first. */
fun stripDaysFor(target: LocalDate): List<LocalDate> =
    (1..COPY_STRIP_DAYS).map { target.minusDays(it.toLong()) }

/** Days from the summary that have something to copy for [mode] (meal mode: only that meal). */
fun daysWithContent(summary: FoodLogSummaryDto, mode: CopyMode): Set<LocalDate> {
    val key = (mode as? CopyMode.Meal)?.let { mealKeyToBackend(it.mealName).lowercase() }
    return summary.days
        .filter { day -> if (key == null) day.meals.isNotEmpty() else day.meals.containsKey(key) }
        .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
        .toSet()
}

fun preselectDay(days: Set<LocalDate>): LocalDate? = days.maxOrNull()

fun candidatesFrom(day: DayResponseDto, mode: CopyMode): List<CopyCandidate> {
    val meals = when (mode) {
        is CopyMode.Meal -> listOf(mode.mealName)
        CopyMode.Day -> MEAL_ORDER
    }
    return meals.flatMap { name ->
        day.foodsByMeal[mealKeyToBackend(name).lowercase()].orEmpty().map { dto ->
            CopyCandidate(dto.id, mealNameFromBackend(dto.meal), dto.toLogged())
        }
    }
}

/**
 * State for the "copy from another day" bottom sheet. Activity-scoped, so logout must call
 * [resetUserState].
 */
class CopyViewModel(
    private val logsApi: LogsApi = RetrofitInstance.logsApi,
) : ViewModel() {

    private val _state = MutableStateFlow(CopyUiState())
    val state: StateFlow<CopyUiState> = _state.asStateFlow()

    private val _copied = MutableSharedFlow<CopyResult>(extraBufferCapacity = 1)
    val copied: SharedFlow<CopyResult> = _copied.asSharedFlow()

    private val _errors = MutableSharedFlow<FoodViewModel.ToastEvent>(extraBufferCapacity = 4)
    val errors: SharedFlow<FoodViewModel.ToastEvent> = _errors.asSharedFlow()

    private var openJob: Job? = null
    private var dayJob: Job? = null
    private var copyJob: Job? = null

    fun open(mode: CopyMode, targetDate: LocalDate) {
        cancelAll()
        _state.value = CopyUiState(
            mode = mode,
            targetDate = targetDate,
            stripDays = stripDaysFor(targetDate),
            summaryLoading = true,
        )
        openJob = viewModelScope.launch {
            try {
                val summary = logsApi.getFoodLogSummary(
                    from = targetDate.minusDays(COPY_PRESELECT_WINDOW_DAYS).toString(),
                    to = targetDate.minusDays(1).toString(),
                )
                val days = daysWithContent(summary, mode)
                val selected = preselectDay(days)
                _state.update {
                    it.copy(
                        summaryLoading = false,
                        daysWithContent = days,
                        selectedDay = selected,
                        extraDay = selected?.takeIf { d -> d !in it.stripDays },
                        source = if (selected == null) SourceDayState.None else SourceDayState.Loading,
                    )
                }
                if (selected != null) loadDay(selected)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(summaryLoading = false, summaryError = R.string.copy_error_load) }
            }
        }
    }

    fun selectDay(day: LocalDate) {
        dayJob?.cancel()
        _state.update {
            it.copy(
                selectedDay = day,
                extraDay = if (day in it.stripDays) it.extraDay else day,
                source = SourceDayState.Loading,
            )
        }
        loadDay(day)
    }

    fun retry() {
        val s = _state.value
        if (s.summaryError != null) {
            open(s.mode, s.targetDate)
        } else {
            val day = s.selectedDay ?: return
            dayJob?.cancel()
            _state.update { it.copy(source = SourceDayState.Loading) }
            loadDay(day)
        }
    }

    private fun loadDay(day: LocalDate) {
        dayJob?.cancel()
        val job = viewModelScope.launch {
            val result = try {
                SourceDayState.Loaded(candidatesFrom(logsApi.getDay(day.toString()), _state.value.mode))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                SourceDayState.Error(R.string.copy_error_load)
            }
            _state.update { if (it.selectedDay == day) it.copy(source = result) else it }
        }
        dayJob = job
    }

    private fun updateCandidates(transform: (List<CopyCandidate>) -> List<CopyCandidate>) {
        _state.update {
            val src = it.source as? SourceDayState.Loaded ?: return@update it
            it.copy(source = SourceDayState.Loaded(transform(src.candidates)))
        }
    }

    private fun editCandidate(sourceId: Long, edit: (CopyCandidate) -> CopyCandidate) =
        updateCandidates { list -> list.map { if (it.sourceId == sourceId) edit(it) else it } }

    fun toggle(sourceId: Long) = editCandidate(sourceId) { it.copy(checked = !it.checked) }

    fun toggleMeal(mealName: String) = updateCandidates { list ->
        val group = list.filter { it.mealName == mealName }
        val target = !group.all { it.checked }
        list.map { if (it.mealName == mealName) it.copy(checked = target) else it }
    }

    fun updatePortion(sourceId: Long, grams: Int, unit: String?, unitQty: Float?) =
        editCandidate(sourceId) {
            it.copy(logged = it.logged.copy(grams = grams, unit = unit, unitQty = unitQty))
        }

    fun updateIngredients(sourceId: Long, ingredients: List<FoodIngredient>) =
        editCandidate(sourceId) {
            it.copy(
                logged = it.logged.copy(
                    food = it.logged.food.copy(ingredients = ingredients),
                    grams = ingredients.sumOf { ing -> ing.grams.toDouble() }.roundToInt(),
                    unit = null,
                    unitQty = null,
                ),
                ingredientsEdited = true,
            )
        }

    fun copy() {
        val s = _state.value
        if (s.copying) return
        val checked = (s.source as? SourceDayState.Loaded)?.candidates?.filter { it.checked }.orEmpty()
        if (checked.isEmpty()) return
        val body = FoodLogCopyRequestDto(
            toDate = s.targetDate.toString(),
            items = checked.map {
                FoodLogCopyItemDto(
                    sourceId = it.sourceId,
                    grams = it.logged.grams,
                    unit = it.logged.unit,
                    unitQty = it.logged.unitQty,
                    ingredients = if (it.ingredientsEdited) it.logged.food.ingredients!!.map { i -> i.toDto() } else null,
                )
            },
        )
        _state.update { it.copy(copying = true) }
        copyJob = viewModelScope.launch {
            try {
                val logs = logsApi.copyFoodLogs(body)
                _state.update { it.copy(copying = false) }
                _copied.tryEmit(CopyResult(body.toDate, logs))
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                _state.update { it.copy(copying = false) }
                _errors.tryEmit(FoodViewModel.ToastEvent(R.string.error_no_internet))
            } catch (e: HttpException) {
                _state.update { it.copy(copying = false) }
                _errors.tryEmit(FoodViewModel.ToastEvent(R.string.error_save, e.code()))
            } catch (e: Throwable) {
                _state.update { it.copy(copying = false) }
                _errors.tryEmit(FoodViewModel.ToastEvent(R.string.error_save))
            }
        }
    }

    private fun cancelAll() {
        openJob?.cancel()
        dayJob?.cancel()
        copyJob?.cancel()
    }

    fun resetUserState() {
        cancelAll()
        _state.value = CopyUiState()
    }
}
