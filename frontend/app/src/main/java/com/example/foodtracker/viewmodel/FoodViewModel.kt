package com.example.foodtracker.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.foodtracker.R
import com.example.foodtracker.api.LogsApi
import com.example.foodtracker.api.ProfileApi
import com.example.foodtracker.api.RetrofitInstance
import com.example.foodtracker.data.FoodRepository
import com.example.foodtracker.data.MealLogTracker
import com.example.foodtracker.data.SettingsRepository
import com.example.foodtracker.model.AppSettings
import com.example.foodtracker.model.FoodIngredient
import com.example.foodtracker.model.FoodItem
import com.example.foodtracker.model.FoodLogCreateDto
import com.example.foodtracker.model.FoodLogUpdateDto
import com.example.foodtracker.model.toDto
import com.example.foodtracker.model.HydrationUpdateDto
import com.example.foodtracker.model.LoggedFood
import com.example.foodtracker.model.NutritionGoals
import com.example.foodtracker.model.UserProfile
import com.example.foodtracker.model.WeightCheckInCreateDto
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

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

class FoodViewModel(
    private val settingsRepository: SettingsRepository,
    private val mealLogTracker: MealLogTracker,
    private val foodRepository: FoodRepository,
    private val logsApi: LogsApi = RetrofitInstance.retrofit.create(LogsApi::class.java),
) : ViewModel() {

    // ── Search state ──────────────────────────────────────────────────────────
    sealed class SearchUiState {
        data object Idle : SearchUiState()
        data object Loading : SearchUiState()
        data class Results(
            val items: List<FoodItem>,
            val stale: Boolean = false,
            val hasMore: Boolean = false,
            val loadingMore: Boolean = false,
        ) : SearchUiState()
        data object Empty : SearchUiState()
        data class Error(@StringRes val messageRes: Int, val arg: Int? = null) : SearchUiState()
    }

    /** A localizable snackbar message; [code] (HTTP status) is appended in the UI when present. */
    data class ToastEvent(@StringRes val messageRes: Int, val code: Int? = null)

    private val profileApi: ProfileApi = RetrofitInstance.retrofit.create(ProfileApi::class.java)

    private val _searchState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    private var currentQuery: String = ""
    private var currentPage: Int = 1

    @OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeSearchQueries() {
        viewModelScope.launch {
            _searchQuery
                .debounce(350)
                .mapLatest { query ->
                    if (query.length < 2) {
                        SearchUiState.Idle
                    } else {
                        currentQuery = query
                        currentPage = 1
                        _searchState.value = SearchUiState.Loading
                        try {
                            val result = foodRepository.search(query)
                            if (result.items.isEmpty()) SearchUiState.Empty
                            else SearchUiState.Results(result.items, result.stale, result.hasMore)
                        } catch (e: java.io.IOException) {
                            SearchUiState.Error(R.string.error_no_internet)
                        } catch (e: retrofit2.HttpException) {
                            if (e.code() == 503) SearchUiState.Error(R.string.error_db_busy)
                            else SearchUiState.Error(R.string.error_unexpected_code, e.code())
                        } catch (e: Throwable) {
                            SearchUiState.Error(R.string.error_generic)
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

    fun loadMore() {
        val current = _searchState.value
        if (current !is SearchUiState.Results) return
        if (!current.hasMore || current.loadingMore) return
        val queryAtStart = currentQuery
        viewModelScope.launch {
            _searchState.value = current.copy(loadingMore = true)
            try {
                val next = currentPage + 1
                val result = foodRepository.search(queryAtStart, next)
                // O căutare nouă a pornit între timp → renunță la rezultat.
                if (currentQuery != queryAtStart) return@launch
                currentPage = next
                val merged = (current.items + result.items).distinctBy { it.barcode }
                _searchState.value = SearchUiState.Results(merged, current.stale, result.hasMore, loadingMore = false)
            } catch (e: Throwable) {
                // Eroarea de load-more e ne-fatală: păstrăm ce avem, oprim paginarea.
                if (currentQuery == queryAtStart) {
                    _searchState.value = current.copy(hasMore = false, loadingMore = false)
                }
            }
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchState.value = SearchUiState.Idle
    }

    fun lookupBarcode(barcode: String) {
        viewModelScope.launch {
            _searchState.value = SearchUiState.Loading
            _searchState.value = try {
                val result = foodRepository.lookupBarcode(barcode)
                if (result.items.isEmpty()) SearchUiState.Empty
                else SearchUiState.Results(result.items, result.stale)
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 404) SearchUiState.Empty
                else SearchUiState.Error(R.string.error_unexpected_code, e.code())
            } catch (e: java.io.IOException) {
                SearchUiState.Error(R.string.error_no_internet)
            } catch (e: Throwable) {
                SearchUiState.Error(R.string.error_generic)
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
    val appSettings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppSettings()
        )

    fun updateAppSettings(settings: AppSettings) {
        viewModelScope.launch {
            settingsRepository.update(settings)
        }
    }

    // ── Nutrition goals (calorie + macros derivate din profil) ────────────────────
    private val _nutritionGoals = MutableStateFlow<NutritionGoals?>(null)
    val nutritionGoals: StateFlow<NutritionGoals?> = _nutritionGoals.asStateFlow()

    fun loadGoals() {
        viewModelScope.launch {
            try {
                val response = profileApi.getGoals()
                if (response.isSuccessful) {
                    _nutritionGoals.value = response.body()?.toDomain()
                }
                // non-2xx (inclusiv 404 fără profil) → rămâne null, UI folosește defaults
            } catch (_: Exception) {
                // network error → rămâne null, UI folosește defaults
            }
        }
    }

    // ── Date-driven food history ───────────────────────────────────────────────
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun dateKey(date: Date): String = dateFormatter.format(date)

    private fun todayKey(): String = dateKey(Date())

    private val _loadingDay = MutableStateFlow(false)
    val loadingDay: StateFlow<Boolean> = _loadingDay.asStateFlow()

    private val _loadedDates = mutableSetOf<String>()

    private val _toastEvents = MutableSharedFlow<ToastEvent>(extraBufferCapacity = 8)
    val toastEvents: SharedFlow<ToastEvent> = _toastEvents.asSharedFlow()

    private val _selectedHomeDate = MutableStateFlow(Date())
    val selectedHomeDate: StateFlow<Date> = _selectedHomeDate.asStateFlow()

    private val _foodsByDate = MutableStateFlow<Map<String, DayMeals>>(emptyMap())

    // Hydration: liters by day key, used by Home quick-add water card.
    private val _hydrationByDate = MutableStateFlow<Map<String, Float>>(emptyMap())
    val hydrationGoalLiters = 2.5f

    val hydrationTodayLiters: StateFlow<Float> = combine(_selectedHomeDate, _hydrationByDate) { date, all ->
        all[dateKey(date)] ?: 0f
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _hydrationByDate.value[todayKey()] ?: 0f)

    private val _weightHistory = MutableStateFlow<List<WeightCheckIn>>(emptyList())
    val weightHistory: StateFlow<List<WeightCheckIn>> = _weightHistory.asStateFlow()

    fun incrementHydration(amountLiters: Float = 0.25f) {
        val key = dateKey(_selectedHomeDate.value)
        val current = _hydrationByDate.value[key] ?: 0f
        val updated = (current + amountLiters).coerceAtMost(8f)
        _hydrationByDate.value = _hydrationByDate.value + (key to updated)

        viewModelScope.launch {
            try {
                logsApi.putHydration(key, HydrationUpdateDto(liters = updated))
            } catch (e: java.io.IOException) {
                _hydrationByDate.value = _hydrationByDate.value + (key to current)
                _toastEvents.tryEmit(ToastEvent(R.string.error_no_internet))
            } catch (e: retrofit2.HttpException) {
                _hydrationByDate.value = _hydrationByDate.value + (key to current)
                _toastEvents.tryEmit(ToastEvent(R.string.error_save_hydration, e.code()))
            } catch (e: Throwable) {
                _hydrationByDate.value = _hydrationByDate.value + (key to current)
                _toastEvents.tryEmit(ToastEvent(R.string.error_save_hydration))
            }
        }
    }

    private fun mealKeyToBackend(meal: String): String = when (meal) {
        "Breakfast" -> "BREAKFAST"
        "Lunch"     -> "LUNCH"
        "Dinner"    -> "DINNER"
        "Snacks"    -> "SNACKS"
        else        -> "BREAKFAST"
    }

    private fun sameDay(a: Date, b: Date): Boolean = dateKey(a) == dateKey(b)

    private suspend fun loadDay(date: Date) {
        val key = dateKey(date)
        if (key in _loadedDates) return
        _loadingDay.value = true
        try {
            val day = logsApi.getDay(key)
            val meals = DayMeals(
                breakfast = day.foodsByMeal["breakfast"].orEmpty().map { it.toLogged() },
                lunch     = day.foodsByMeal["lunch"].orEmpty().map { it.toLogged() },
                dinner    = day.foodsByMeal["dinner"].orEmpty().map { it.toLogged() },
                snacks    = day.foodsByMeal["snacks"].orEmpty().map { it.toLogged() },
            )
            _foodsByDate.value = _foodsByDate.value + (key to meals)
            _hydrationByDate.value = _hydrationByDate.value + (key to day.hydrationLiters)
            day.weightCheckIn?.let { wc ->
                val parsed = dateFormatter.parse(wc.date) ?: return@let
                val existing = _weightHistory.value.filterNot { sameDay(it.date, parsed) }
                _weightHistory.value = (existing + WeightCheckIn(parsed, wc.weightKg))
                    .sortedBy { it.date }
            }
            _loadedDates += key
        } catch (e: java.io.IOException) {
            _toastEvents.tryEmit(ToastEvent(R.string.error_no_internet))
        } catch (e: retrofit2.HttpException) {
            _toastEvents.tryEmit(ToastEvent(R.string.error_load_day, e.code()))
        } catch (e: Throwable) {
            _toastEvents.tryEmit(ToastEvent(R.string.error_load_day))
        } finally {
            _loadingDay.value = false
        }
    }

    fun loadWeightHistory(daysBack: Int = 30) {
        viewModelScope.launch {
            val cal = Calendar.getInstance()
            val today = dateFormatter.format(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, -daysBack)
            val from = dateFormatter.format(cal.time)
            try {
                val response = logsApi.getWeightCheckIns(from = from, to = today)
                _weightHistory.value = response.items.mapNotNull { dto ->
                    val parsed = dateFormatter.parse(dto.date) ?: return@mapNotNull null
                    WeightCheckIn(parsed, dto.weightKg)
                }
            } catch (e: java.io.IOException) {
                _toastEvents.tryEmit(ToastEvent(R.string.error_no_internet))
            } catch (e: retrofit2.HttpException) {
                _toastEvents.tryEmit(ToastEvent(R.string.error_weight_history, e.code()))
            } catch (e: Throwable) {
                _toastEvents.tryEmit(ToastEvent(R.string.error_weight_history))
            }
        }
    }

    fun addWeightCheckIn(weightKg: Float, date: Date = _selectedHomeDate.value) {
        val dateStr = dateFormatter.format(date)
        val previousHistory = _weightHistory.value
        val previousProfileWeight = _userProfile.value.currentWeightKg

        // Optimistic: replace or append today's point
        val withoutToday = previousHistory.filterNot { sameDay(it.date, date) }
        val newHistory = (withoutToday + WeightCheckIn(date, weightKg)).sortedBy { it.date }
        _weightHistory.value = newHistory

        // If the selected day is now the most recent, sync local profile
        val isMostRecent = newHistory.lastOrNull()?.let { sameDay(it.date, date) } == true
        if (isMostRecent) {
            _userProfile.value = _userProfile.value.copy(currentWeightKg = weightKg)
        }

        viewModelScope.launch {
            try {
                logsApi.postWeightCheckIn(WeightCheckInCreateDto(date = dateStr, weightKg = weightKg))
            } catch (e: java.io.IOException) {
                _weightHistory.value = previousHistory
                _userProfile.value = _userProfile.value.copy(currentWeightKg = previousProfileWeight)
                _toastEvents.tryEmit(ToastEvent(R.string.error_no_internet))
            } catch (e: retrofit2.HttpException) {
                _weightHistory.value = previousHistory
                _userProfile.value = _userProfile.value.copy(currentWeightKg = previousProfileWeight)
                _toastEvents.tryEmit(ToastEvent(R.string.error_weight_checkin, e.code()))
            } catch (e: Throwable) {
                _weightHistory.value = previousHistory
                _userProfile.value = _userProfile.value.copy(currentWeightKg = previousProfileWeight)
                _toastEvents.tryEmit(ToastEvent(R.string.error_weight_checkin))
            }
        }
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
        viewModelScope.launch { loadDay(date) }
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
        addFoodToMealReturningIndex(mealName, food)
    }

    fun addFoodToMealReturningIndex(mealName: String, food: LoggedFood): Int {
        val tempId = UUID.randomUUID().toString()
        val optimistic = food.copy(id = null, clientTempId = tempId)
        val key = dateKey(_selectedHomeDate.value)
        val day = _foodsByDate.value[key] ?: DayMeals()
        val currentList = mealList(day, mealName)
        val insertedIndex = currentList.size
        _foodsByDate.value = _foodsByDate.value + (key to updateMealList(day, mealName, currentList + optimistic))

        viewModelScope.launch {
            try {
                val response = logsApi.createFoodLog(
                    FoodLogCreateDto(
                        logDate = key,
                        meal = mealKeyToBackend(mealName),
                        grams = optimistic.grams,
                        barcode = optimistic.food.barcode,
                        name = optimistic.food.name,
                        brand = optimistic.food.brand,
                        imageUrl = optimistic.food.imageUrl,
                        categories = optimistic.food.categories,
                        kcal100g = optimistic.food.per100g.toFloat(),
                        protein100g = optimistic.food.protein100g,
                        carbs100g = optimistic.food.carbs100g,
                        fat100g = optimistic.food.fat100g,
                        ingredients = optimistic.food.ingredients?.map { it.toDto() },
                    )
                )
                replaceByTempId(key, mealName, tempId) { it.copy(id = response.id, clientTempId = null) }
                // Hint local pentru worker-ul de remindere — sursa de adevăr rămâne backend-ul.
                launch {
                    runCatching {
                        mealLogTracker.markLogged(LocalDate.parse(key), mealName)
                    }
                }
            } catch (e: java.io.IOException) {
                removeByTempId(key, mealName, tempId)
                _toastEvents.tryEmit(ToastEvent(R.string.error_no_internet))
            } catch (e: retrofit2.HttpException) {
                removeByTempId(key, mealName, tempId)
                _toastEvents.tryEmit(ToastEvent(R.string.error_save, e.code()))
            } catch (e: Throwable) {
                removeByTempId(key, mealName, tempId)
                _toastEvents.tryEmit(ToastEvent(R.string.error_save))
            }
        }
        return insertedIndex
    }

    private fun mealList(day: DayMeals, mealName: String): List<LoggedFood> = when (mealName) {
        "Breakfast" -> day.breakfast
        "Lunch"     -> day.lunch
        "Dinner"    -> day.dinner
        "Snacks"    -> day.snacks
        else        -> day.breakfast
    }

    private fun updateMealList(day: DayMeals, mealName: String, newList: List<LoggedFood>): DayMeals =
        when (mealName) {
            "Breakfast" -> day.copy(breakfast = newList)
            "Lunch"     -> day.copy(lunch = newList)
            "Dinner"    -> day.copy(dinner = newList)
            "Snacks"    -> day.copy(snacks = newList)
            else        -> day.copy(breakfast = newList)
        }

    private fun replaceByTempId(
        key: String,
        mealName: String,
        tempId: String,
        transform: (LoggedFood) -> LoggedFood,
    ) {
        val day = _foodsByDate.value[key] ?: return
        val list = mealList(day, mealName)
        val newList = list.map { if (it.clientTempId == tempId) transform(it) else it }
        _foodsByDate.value = _foodsByDate.value + (key to updateMealList(day, mealName, newList))
    }

    private fun removeByTempId(key: String, mealName: String, tempId: String) {
        val day = _foodsByDate.value[key] ?: return
        val list = mealList(day, mealName)
        val newList = list.filterNot { it.clientTempId == tempId }
        _foodsByDate.value = _foodsByDate.value + (key to updateMealList(day, mealName, newList))
    }

    fun removeFoodFromMeal(mealName: String, index: Int) {
        val key = dateKey(_selectedHomeDate.value)
        val day = _foodsByDate.value[key] ?: return
        val list = mealList(day, mealName)
        if (index !in list.indices) return
        val target = list[index]
        val id = target.id ?: return  // still pending POST — UI should disable DELETE

        // Optimistic remove
        val newList = list.toMutableList().also { it.removeAt(index) }
        _foodsByDate.value = _foodsByDate.value + (key to updateMealList(day, mealName, newList))

        viewModelScope.launch {
            val response = try {
                logsApi.deleteFoodLog(id)
            } catch (e: java.io.IOException) {
                restoreFood(key, mealName, index, target)
                _toastEvents.tryEmit(ToastEvent(R.string.error_no_internet))
                return@launch
            } catch (e: retrofit2.HttpException) {
                restoreFood(key, mealName, index, target)
                _toastEvents.tryEmit(ToastEvent(R.string.error_delete, e.code()))
                return@launch
            } catch (e: Throwable) {
                restoreFood(key, mealName, index, target)
                _toastEvents.tryEmit(ToastEvent(R.string.error_delete))
                return@launch
            }
            if (!response.isSuccessful) {
                restoreFood(key, mealName, index, target)
                _toastEvents.tryEmit(ToastEvent(R.string.error_delete, response.code()))
            }
        }
    }

    private fun restoreFood(key: String, mealName: String, index: Int, food: LoggedFood) {
        val day = _foodsByDate.value[key] ?: DayMeals()
        val list = mealList(day, mealName).toMutableList()
        val insertAt = index.coerceIn(0, list.size)
        list.add(insertAt, food)
        _foodsByDate.value = _foodsByDate.value + (key to updateMealList(day, mealName, list))
    }

    fun updateFoodGrams(mealName: String, index: Int, newGrams: Int) {
        val key = dateKey(_selectedHomeDate.value)
        val day = _foodsByDate.value[key] ?: return
        val list = mealList(day, mealName)
        if (index !in list.indices) return
        val target = list[index]
        val id = target.id ?: return          // încă în POST pending — edit indisponibil
        if (newGrams == target.grams) return  // no-op

        // Optimistic update (pe loc, fără reordonare)
        val optimistic = target.copy(grams = newGrams)
        val newList = list.toMutableList().also { it[index] = optimistic }
        _foodsByDate.value = _foodsByDate.value + (key to updateMealList(day, mealName, newList))

        viewModelScope.launch {
            try {
                logsApi.updateFoodLog(id, FoodLogUpdateDto(grams = newGrams))
            } catch (e: java.io.IOException) {
                restoreGrams(key, mealName, id, target.grams)
                _toastEvents.tryEmit(ToastEvent(R.string.error_no_internet))
            } catch (e: retrofit2.HttpException) {
                restoreGrams(key, mealName, id, target.grams)
                _toastEvents.tryEmit(ToastEvent(R.string.error_save, e.code()))
            } catch (e: Throwable) {
                restoreGrams(key, mealName, id, target.grams)
                _toastEvents.tryEmit(ToastEvent(R.string.error_save))
            }
        }
    }

    private fun restoreGrams(key: String, mealName: String, id: Long, oldGrams: Int) {
        val day = _foodsByDate.value[key] ?: return
        val list = mealList(day, mealName)
        val newList = list.map { if (it.id == id) it.copy(grams = oldGrams) else it }
        _foodsByDate.value = _foodsByDate.value + (key to updateMealList(day, mealName, newList))
    }

    /** Edit a recipe portion: recompute totals from the (possibly added/removed)
     *  ingredients, optimistic-update in place, PATCH with ingredients, rollback on error. */
    fun updateRecipeLog(mealName: String, index: Int, ingredients: List<FoodIngredient>) {
        val key = dateKey(_selectedHomeDate.value)
        val day = _foodsByDate.value[key] ?: return
        val list = mealList(day, mealName)
        if (index !in list.indices) return
        val target = list[index]
        val id = target.id ?: return          // încă în POST pending — edit indisponibil
        if (ingredients.isEmpty()) return

        val totalG = ingredients.sumOf { it.grams.toDouble() }.coerceAtLeast(1.0)
        val totalKcal = ingredients.sumOf { it.kcal.toDouble() }
        val totalP = ingredients.sumOf { it.protein.toDouble() }
        val totalC = ingredients.sumOf { it.carbs.toDouble() }
        val totalF = ingredients.sumOf { it.fat.toDouble() }
        val newFood = target.food.copy(
            per100g = (totalKcal / totalG * 100).toInt(),
            protein100g = (totalP / totalG * 100).toFloat(),
            carbs100g = (totalC / totalG * 100).toFloat(),
            fat100g = (totalF / totalG * 100).toFloat(),
            ingredients = ingredients,
        )
        val optimistic = target.copy(food = newFood, grams = totalG.toInt())
        val newList = list.toMutableList().also { it[index] = optimistic }
        _foodsByDate.value = _foodsByDate.value + (key to updateMealList(day, mealName, newList))

        viewModelScope.launch {
            try {
                logsApi.updateFoodLog(
                    id,
                    FoodLogUpdateDto(grams = totalG.toInt(), ingredients = ingredients.map { it.toDto() }),
                )
            } catch (e: java.io.IOException) {
                restoreLog(key, mealName, id, target)
                _toastEvents.tryEmit(ToastEvent(R.string.error_no_internet))
            } catch (e: retrofit2.HttpException) {
                restoreLog(key, mealName, id, target)
                _toastEvents.tryEmit(ToastEvent(R.string.error_save, e.code()))
            } catch (e: Throwable) {
                restoreLog(key, mealName, id, target)
                _toastEvents.tryEmit(ToastEvent(R.string.error_save))
            }
        }
    }

    private fun restoreLog(key: String, mealName: String, id: Long, original: LoggedFood) {
        val day = _foodsByDate.value[key] ?: return
        val list = mealList(day, mealName)
        val newList = list.map { if (it.id == id) original else it }
        _foodsByDate.value = _foodsByDate.value + (key to updateMealList(day, mealName, newList))
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

    init {
        viewModelScope.launch { loadDay(Date()) }
        loadGoals()
    }
}