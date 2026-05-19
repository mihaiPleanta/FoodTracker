package com.example.foodtracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.foodtracker.api.FoodApi
import com.example.foodtracker.api.RetrofitInstance
import com.example.foodtracker.model.AppSettings
import com.example.foodtracker.model.FoodItem
import com.example.foodtracker.model.LoggedFood
import com.example.foodtracker.model.UserProfile
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class DayMeals(
    val breakfast: List<LoggedFood> = emptyList(),
    val lunch: List<LoggedFood> = emptyList(),
    val dinner: List<LoggedFood> = emptyList(),
    val snacks: List<LoggedFood> = emptyList()
)

data class WeightCheckIn(
    val date: Date,
    val weightKg: Float
)

class FoodViewModel : ViewModel() {

    // ── Search state ──────────────────────────────────────────────────────────
    sealed class SearchUiState {
        data object Idle : SearchUiState()
        data object Loading : SearchUiState()
        data class Results(val items: List<FoodItem>) : SearchUiState()
        data object Empty : SearchUiState()
        data class Error(val message: String) : SearchUiState()
    }

    private val foodApi: FoodApi = RetrofitInstance.retrofit.create(FoodApi::class.java)

    private val _searchState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    @OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeSearchQueries() {
        viewModelScope.launch {
            _searchQuery
                .debounce(350)
                .mapLatest { query ->
                    if (query.length < 2) {
                        SearchUiState.Idle
                    } else {
                        _searchState.value = SearchUiState.Loading
                        try {
                            val response = foodApi.searchFoods(query)
                            val items = response.items.map { it.toDomain() }
                            if (items.isEmpty()) SearchUiState.Empty
                            else SearchUiState.Results(items)
                        } catch (e: java.io.IOException) {
                            SearchUiState.Error("Verifică conexiunea la internet")
                        } catch (e: retrofit2.HttpException) {
                            if (e.code() == 503) SearchUiState.Error("Baza de date e ocupată, încearcă din nou")
                            else SearchUiState.Error("Eroare neașteptată (${e.code()})")
                        } catch (e: Throwable) {
                            SearchUiState.Error("Eroare neașteptată")
                        }
                    }
                }
                .collect { state -> _searchState.value = state }
        }
    }

    init {
        observeSearchQueries()
    }

    fun searchFoods(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchState.value = SearchUiState.Idle
    }

    fun lookupBarcode(barcode: String) {
        viewModelScope.launch {
            _searchState.value = SearchUiState.Loading
            _searchState.value = try {
                val dto = foodApi.getFoodByBarcode(barcode)
                SearchUiState.Results(listOf(dto.toDomain()))
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 404) SearchUiState.Empty
                else SearchUiState.Error("Eroare la căutarea produsului (${e.code()})")
            } catch (e: java.io.IOException) {
                SearchUiState.Error("Verifică conexiunea la internet")
            } catch (e: Throwable) {
                SearchUiState.Error("Eroare neașteptată")
            }
        }
    }

    // ── Meal selector pop-up state ────────────────────────────────────────────
    private val _showMealSelector = MutableStateFlow(false)
    val showMealSelector: StateFlow<Boolean> = _showMealSelector.asStateFlow()

    fun openMealSelector()  { _showMealSelector.value = true  }
    fun closeMealSelector() { _showMealSelector.value = false }
    fun toggleMealSelector() { _showMealSelector.value = !_showMealSelector.value }

    // ── User profile ──────────────────────────────────────────────────────────────
    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()
    fun updateUserProfile(profile: UserProfile) { _userProfile.value = profile }

    // ── App settings ──────────────────────────────────────────────────────────────
    private val _appSettings = MutableStateFlow(AppSettings())
    val appSettings: StateFlow<AppSettings> = _appSettings.asStateFlow()
    fun updateAppSettings(settings: AppSettings) { _appSettings.value = settings }

    // ── Date-driven food history ───────────────────────────────────────────────
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun dateKey(date: Date): String = dateFormatter.format(date)

    private fun todayKey(): String = dateKey(Date())

    private val _selectedHomeDate = MutableStateFlow(Date())
    val selectedHomeDate: StateFlow<Date> = _selectedHomeDate.asStateFlow()

    private val _foodsByDate = MutableStateFlow<Map<String, DayMeals>>(emptyMap())

    // Hydration: liters by day key, used by Home quick-add water card.
    private val _hydrationByDate = MutableStateFlow(
        mapOf(
            todayKey() to 0.8f
        )
    )
    val hydrationGoalLiters = 2.5f

    val hydrationTodayLiters: StateFlow<Float> = combine(_selectedHomeDate, _hydrationByDate) { date, all ->
        all[dateKey(date)] ?: 0f
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _hydrationByDate.value[todayKey()] ?: 0f)

    // Weight check-ins: local seed for the mini trend card.
    private fun seedWeightHistory(): List<WeightCheckIn> {
        val now = Calendar.getInstance()
        val points = listOf(78.7f, 78.5f, 78.6f, 78.4f, 78.3f, 78.2f, 78.1f)
        return points.mapIndexed { index, kg ->
            val c = now.clone() as Calendar
            c.add(Calendar.DAY_OF_YEAR, index - (points.size - 1))
            WeightCheckIn(date = c.time, weightKg = kg)
        }
    }

    private val _weightHistory = MutableStateFlow(seedWeightHistory())
    val weightHistory: StateFlow<List<WeightCheckIn>> = _weightHistory.asStateFlow()

    fun incrementHydration(amountLiters: Float = 0.25f) {
        val key = dateKey(_selectedHomeDate.value)
        val current = _hydrationByDate.value[key] ?: 0f
        val updated = (current + amountLiters).coerceAtMost(8f)
        _hydrationByDate.value = _hydrationByDate.value + (key to updated)
    }

    fun setSelectedHomeDate(date: Date) {
        val key = dateKey(date)
        if (!_foodsByDate.value.containsKey(key)) {
            _foodsByDate.value = _foodsByDate.value + (key to DayMeals())
        }
        if (!_hydrationByDate.value.containsKey(key)) {
            _hydrationByDate.value = _hydrationByDate.value + (key to 0f)
        }
        _selectedHomeDate.value = date
    }

    val breakfastFoods: StateFlow<List<LoggedFood>> = combine(_selectedHomeDate, _foodsByDate) { date, all ->
        all[dateKey(date)]?.breakfast ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val lunchFoods: StateFlow<List<LoggedFood>> = combine(_selectedHomeDate, _foodsByDate) { date, all ->
        all[dateKey(date)]?.lunch ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dinnerFoods: StateFlow<List<LoggedFood>> = combine(_selectedHomeDate, _foodsByDate) { date, all ->
        all[dateKey(date)]?.dinner ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val snacksFoods: StateFlow<List<LoggedFood>> = combine(_selectedHomeDate, _foodsByDate) { date, all ->
        all[dateKey(date)]?.snacks ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun getFoodsFlow(mealName: String): StateFlow<List<LoggedFood>> = when (mealName) {
        "Breakfast" -> breakfastFoods
        "Lunch"     -> lunchFoods
        "Dinner"    -> dinnerFoods
        "Snacks"    -> snacksFoods
        else         -> breakfastFoods
    }

    fun addFoodToMeal(mealName: String, food: LoggedFood) {
        val key = dateKey(_selectedHomeDate.value)
        val day = _foodsByDate.value[key] ?: DayMeals()
        val updatedDay = when (mealName) {
            "Breakfast" -> day.copy(breakfast = day.breakfast + food)
            "Lunch"     -> day.copy(lunch = day.lunch + food)
            "Dinner"    -> day.copy(dinner = day.dinner + food)
            "Snacks"    -> day.copy(snacks = day.snacks + food)
            else         -> day.copy(breakfast = day.breakfast + food)
        }
        _foodsByDate.value = _foodsByDate.value + (key to updatedDay)
    }

    fun removeFoodFromMeal(mealName: String, index: Int) {
        val key = dateKey(_selectedHomeDate.value)
        val day = _foodsByDate.value[key] ?: DayMeals()

        fun List<LoggedFood>.removeAtSafe(targetIndex: Int): List<LoggedFood> {
            if (targetIndex !in indices) return this
            return toMutableList().also { it.removeAt(targetIndex) }
        }

        val updatedDay = when (mealName) {
            "Breakfast" -> day.copy(breakfast = day.breakfast.removeAtSafe(index))
            "Lunch"     -> day.copy(lunch = day.lunch.removeAtSafe(index))
            "Dinner"    -> day.copy(dinner = day.dinner.removeAtSafe(index))
            "Snacks"    -> day.copy(snacks = day.snacks.removeAtSafe(index))
            else         -> day.copy(breakfast = day.breakfast.removeAtSafe(index))
        }

        _foodsByDate.value = _foodsByDate.value + (key to updatedDay)
    }

    // ── Computed totals ───────────────────────────────────────────────────────
    fun getMealCalories(mealName: String)  = getFoodsFlow(mealName).value.sumOf { it.calories }
    fun getMealProtein(mealName: String)   = getFoodsFlow(mealName).value.sumOf { it.protein.toDouble() }.toFloat()
    fun getMealCarbs(mealName: String)     = getFoodsFlow(mealName).value.sumOf { it.carbs.toDouble() }.toFloat()
    fun getMealFat(mealName: String)       = getFoodsFlow(mealName).value.sumOf { it.fat.toDouble() }.toFloat()

    fun getTotalCalories() = listOf("Breakfast","Lunch","Dinner","Snacks").sumOf { getMealCalories(it) }
    fun getTotalProtein()  = listOf("Breakfast","Lunch","Dinner","Snacks").sumOf { getMealProtein(it).toDouble() }.toInt()
    fun getTotalCarbs()    = listOf("Breakfast","Lunch","Dinner","Snacks").sumOf { getMealCarbs(it).toDouble() }.toInt()
    fun getTotalFat()      = listOf("Breakfast","Lunch","Dinner","Snacks").sumOf { getMealFat(it).toDouble() }.toInt()

}