package com.example.foodtracker.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.foodtracker.R
import com.example.foodtracker.api.ProfileApi
import com.example.foodtracker.api.RetrofitInstance
import com.example.foodtracker.data.FoodRepository
import com.example.foodtracker.data.MealLogTracker
import com.example.foodtracker.data.SettingsRepository
import com.example.foodtracker.data.journal.JournalRepository
import com.example.foodtracker.data.journal.LocalDay
import com.example.foodtracker.data.journal.SyncEvent
import com.example.foodtracker.model.AppSettings
import com.example.foodtracker.model.DEFAULT_HYDRATION_GOAL_L
import com.example.foodtracker.model.DayStatus
import com.example.foodtracker.model.FoodIngredient
import com.example.foodtracker.model.FoodItem
import com.example.foodtracker.model.LoggedFood
import com.example.foodtracker.model.NutritionGoals
import com.example.foodtracker.model.UserProfile
import com.example.foodtracker.model.mealNameFromBackend
import com.example.foodtracker.model.toProfileDto
import com.example.foodtracker.model.totalGrams
import com.example.foodtracker.model.withIngredients
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val HYDRATION_SAVE_DEBOUNCE_MS = 600L

/** Home's weight card / stats trend show the last 30 days of check-ins. */
private const val WEIGHT_HISTORY_DAYS = 30L

data class WeightCheckIn(
    val date: Date,
    val weightKg: Float
)

class FoodViewModel(
    private val settingsRepository: SettingsRepository,
    private val mealLogTracker: MealLogTracker,
    private val foodRepository: FoodRepository,
    private val journal: JournalRepository,
    private val profileApi: ProfileApi = RetrofitInstance.retrofit.create(ProfileApi::class.java),
    private val clock: () -> Date = { Date() },
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

    private val _searchState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    /** Barcode of the last scan lookup; null once a text search or clear replaces it. */
    private val _lastLookupBarcode = MutableStateFlow<String?>(null)
    val lastLookupBarcode: StateFlow<String?> = _lastLookupBarcode.asStateFlow()
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
        _lastLookupBarcode.value = null
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
        _lastLookupBarcode.value = null
        _searchQuery.value = ""
        _searchState.value = SearchUiState.Idle
    }

    fun lookupBarcode(barcode: String) {
        _lastLookupBarcode.value = barcode
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

    // Last profile received from auth (login / onboarding / profile edit). MainActivity's
    // LaunchedEffect re-delivers the same value on Activity recreation; ignoring it keeps
    // local changes made since (hydration prefs, weight) from being overwritten.
    private var lastAppliedAuthProfile: UserProfile? = null

    /** Applies a profile coming from auth and (re)loads goals, which need the profile to exist. */
    fun applyLoadedProfile(profile: UserProfile) {
        if (profile == lastAppliedAuthProfile) return
        lastAppliedAuthProfile = profile
        _userProfile.value = profile
        loadGoals()
    }

    // ── App settings ──────────────────────────────────────────────────────────────
    val appSettings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppSettings()
        )

    /** Only values read from storage — no AppSettings() placeholder (used by reminder sync). */
    val persistedSettings: Flow<AppSettings> = settingsRepository.settings

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

    // ── Date-driven journal (local mirror + op queue, see JournalRepository) ──
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun dateKey(date: Date): String = dateFormatter.format(date)

    private fun todayKey(): String = dateKey(clock())

    private fun localDate(date: Date): LocalDate = LocalDate.parse(dateKey(date))

    private val _loadingDay = MutableStateFlow(false)
    /** True while the selected day is fetched for the first time (a day already in the mirror shows instantly). */
    val loadingDay: StateFlow<Boolean> = _loadingDay.asStateFlow()

    private val _toastEvents = MutableSharedFlow<ToastEvent>(extraBufferCapacity = 8)
    val toastEvents: SharedFlow<ToastEvent> = _toastEvents.asSharedFlow()

    private val _selectedHomeDate = MutableStateFlow(clock())
    val selectedHomeDate: StateFlow<Date> = _selectedHomeDate.asStateFlow()

    /**
     * The mirror's view of the selected day; null until the mirror emits for the date currently
     * selected (so a date switch never shows the previous day's rows).
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val selectedDay: StateFlow<LocalDay?> = _selectedHomeDate
        .map { localDate(it) }
        .distinctUntilChanged()
        .flatMapLatest { journal.observeDay(it) }
        .let { days -> combine(_selectedHomeDate, days) { selected, day -> day?.takeIf { it.date == localDate(selected) } } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val hydrationGoalLiters: StateFlow<Float> = _nutritionGoals
        .map { it?.hydration?.goalL ?: DEFAULT_HYDRATION_GOAL_L }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_HYDRATION_GOAL_L)

    // Salvarea preferințelor de hidratare (creatină / obiectiv manual): optimist, cu debounce
    // opțional pentru stepper; la eșec revenim la profilul de dinainte de prima modificare nesalvată.
    private var hydrationSaveJob: Job? = null
    private var hydrationPrefsBeforeEdit: UserProfile? = null

    fun updateHydrationPrefs(takesCreatine: Boolean, overrideL: Float?, debounce: Boolean = false) {
        if (hydrationPrefsBeforeEdit == null) hydrationPrefsBeforeEdit = _userProfile.value
        hydrationSaveJob?.cancel()
        _userProfile.value = _userProfile.value.copy(
            takesCreatine = takesCreatine,
            hydrationGoalOverrideL = overrideL,
        )
        hydrationSaveJob = viewModelScope.launch {
            if (debounce) delay(HYDRATION_SAVE_DEBOUNCE_MS)
            val success = try {
                profileApi.saveProfile(_userProfile.value.toProfileDto()).isSuccessful
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (success) {
                hydrationPrefsBeforeEdit = null
                loadGoals()
            } else {
                hydrationPrefsBeforeEdit?.let { before ->
                    _userProfile.value = _userProfile.value.copy(
                        takesCreatine = before.takesCreatine,
                        hydrationGoalOverrideL = before.hydrationGoalOverrideL,
                    )
                }
                hydrationPrefsBeforeEdit = null
                _toastEvents.tryEmit(ToastEvent(R.string.error_save_hydration_prefs))
            }
        }
    }

    val hydrationTodayLiters: StateFlow<Float> = selectedDay.map { it?.hydrationL ?: 0f }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)

    /** Weight check-ins of the last [WEIGHT_HISTORY_DAYS] days, from the mirror. */
    val weightHistory: StateFlow<List<WeightCheckIn>> = journal.observeWeights()
        .map { list ->
            val from = localDate(clock()).minusDays(WEIGHT_HISTORY_DAYS)
            list.filter { !it.first.isBefore(from) }
                .mapNotNull { (d, kg) -> dateFormatter.parse(d.toString())?.let { WeightCheckIn(it, kg) } }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Serializes read-modify-write of the hydration counter so quick taps never lose an increment.
    private val hydrationMutex = Mutex()

    fun incrementHydration(amountLiters: Float = 0.25f) {
        val date = localDate(_selectedHomeDate.value)
        launchWrite {
            hydrationMutex.withLock {
                val current = journal.observeDay(date).first().hydrationL
                journal.setHydration(date, (current + amountLiters).coerceAtMost(8f))
            }
        }
    }

    private fun sameDay(a: Date, b: Date): Boolean = dateKey(a) == dateKey(b)

    /** Runs a local journal write; a storage failure becomes a snackbar instead of a crash. */
    private fun launchWrite(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _toastEvents.tryEmit(ToastEvent(R.string.error_save))
        }
    }

    /** Best-effort server refresh; only a day the mirror has never seen reports a failure. */
    private suspend fun refreshDay(date: LocalDate) {
        val known = journal.observeDay(date).first().fetched
        if (!known) _loadingDay.value = true
        try {
            journal.refreshDay(date)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (!journal.observeDay(date).first().fetched && !journal.isCovered(date, date)) {
                _toastEvents.tryEmit(
                    when (e) {
                        is java.io.IOException -> ToastEvent(R.string.error_no_internet)
                        is retrofit2.HttpException -> ToastEvent(R.string.error_load_day, e.code())
                        else -> ToastEvent(R.string.error_load_day)
                    }
                )
            }
        } finally {
            if (!known) _loadingDay.value = false
        }
    }

    // ── Day completion status ─────────────────────────────────────────────────
    // Yesterday-prompt state is keyed to the date it was answered for, so it can't go stale after midnight.
    private val _yesterdayAnsweredFor = MutableStateFlow<String?>(null)
    private var yesterdayRequestedFor: String? = null

    private val _dayStatusChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    /** Emits after every local status write (set, answer, clear) and after every sync. */
    val dayStatusChanged: SharedFlow<Unit> = _dayStatusChanged.asSharedFlow()

    private val _foodLogsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    /** Emits after every local food log write and after every sync (streak refresh). */
    val foodLogsChanged: SharedFlow<Unit> = _foodLogsChanged.asSharedFlow()

    val selectedDayStatus: StateFlow<DayStatus?> = selectedDay.map { it?.status }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val selectedDayHasFood: StateFlow<Boolean> = selectedDay.map { it.hasFood() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** True when the selected Home date is after today (day keys are ISO, so string order = date order). */
    val selectedDayIsFuture: StateFlow<Boolean> = _selectedHomeDate.map { dateKey(it) > todayKey() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private fun yesterday(): Date =
        Calendar.getInstance().apply { time = clock(); add(Calendar.DAY_OF_YEAR, -1) }.time

    private fun yesterdayKey(): String = dateKey(yesterday())

    // Re-derived on every date selection, so after midnight it follows the new yesterday.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val yesterdayDay: Flow<LocalDay> = _selectedHomeDate
        .map { localDate(yesterday()) }
        .distinctUntilChanged()
        .flatMapLatest { journal.observeDay(it) }

    val showYesterdayPrompt: StateFlow<Boolean> = combine(
        _selectedHomeDate, yesterdayDay, _yesterdayAnsweredFor,
    ) { selected, yDay, answeredFor ->
        val key = yesterdayKey()
        dateKey(selected) == todayKey() &&
            yDay.date.toString() == key && yDay.hasFood() &&
            answeredFor != key && yDay.status == null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Refreshes yesterday once per date (without touching the selected date or [loadingDay]). */
    private fun loadYesterdayIfToday() {
        val key = yesterdayKey()
        if (yesterdayRequestedFor == key || dateKey(_selectedHomeDate.value) != todayKey()) return
        yesterdayRequestedFor = key
        viewModelScope.launch {
            try {
                journal.refreshDay(LocalDate.parse(key))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Best-effort: the mirror may already know yesterday; retry on the next visit to today.
                if (yesterdayRequestedFor == key) yesterdayRequestedFor = null
            }
        }
    }

    fun setDayComplete(date: Date, complete: Boolean) {
        if (complete && dateKey(date) > todayKey()) return // a future day cannot be finished yet
        writeStatus(date, complete)
    }

    fun clearDayStatus(date: Date) = writeStatus(date, null)

    fun answerYesterdayPrompt(complete: Boolean) {
        _yesterdayAnsweredFor.value = yesterdayKey()
        setDayComplete(yesterday(), complete)
    }

    private fun writeStatus(date: Date, complete: Boolean?) {
        val day = localDate(date)
        launchWrite {
            journal.setDayStatus(day, complete)
            _dayStatusChanged.tryEmit(Unit)
        }
    }

    /** No-op kept for the screens: weights come from the mirror, which is refreshed on resume. */
    @Suppress("UNUSED_PARAMETER")
    fun loadWeightHistory(daysBack: Int = 30) = Unit

    // Dates with a weight written locally and not yet synced: goals are reloaded once the server has it.
    private val weightDatesAwaitingSync = mutableSetOf<String>()

    fun addWeightCheckIn(weightKg: Float, date: Date = _selectedHomeDate.value) {
        // If the day is now the most recent check-in, keep the local profile in step.
        val isMostRecent = weightHistory.value.none { it.date.after(date) && !sameDay(it.date, date) }
        if (isMostRecent) {
            _userProfile.value = _userProfile.value.copy(currentWeightKg = weightKg)
        }
        val day = localDate(date)
        weightDatesAwaitingSync += day.toString()
        launchWrite { journal.setWeight(day, weightKg) }
    }

    fun setSelectedHomeDate(date: Date) {
        _selectedHomeDate.value = date
        viewModelScope.launch { refreshDay(localDate(date)) }
        loadYesterdayIfToday()
    }

    private fun mealFlow(mealName: String): StateFlow<List<LoggedFood>> =
        selectedDay.map { it?.meals?.get(mealName).orEmpty() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val breakfastFoods: StateFlow<List<LoggedFood>> = mealFlow("Breakfast")
    val lunchFoods: StateFlow<List<LoggedFood>> = mealFlow("Lunch")
    val dinnerFoods: StateFlow<List<LoggedFood>> = mealFlow("Dinner")
    val snacksFoods: StateFlow<List<LoggedFood>> = mealFlow("Snacks")

    fun getFoodsFlow(mealName: String): StateFlow<List<LoggedFood>> = when (mealName) {
        "Breakfast" -> breakfastFoods
        "Lunch"     -> lunchFoods
        "Dinner"    -> dinnerFoods
        "Snacks"    -> snacksFoods
        else         -> breakfastFoods
    }

    /** Current rows of [mealName] on the selected day (empty while the mirror hasn't emitted for it). */
    private fun currentMeal(mealName: String): List<LoggedFood> =
        selectedDay.value?.meals?.get(mealName).orEmpty()

    /**
     * An add whose index was handed to the UI before the mirror shows the row. The MealDetail
     * undo removes by that index, so it is resolved through [clientId] until the row is visible.
     */
    private class PendingAdd(val date: LocalDate, val meal: String, val index: Int) {
        /** Completes with the new clientId, or null if the write failed. */
        val clientId = CompletableDeferred<String?>()
    }

    private val pendingAdds = mutableListOf<PendingAdd>()

    /** Forgets adds the mirror now shows, adds that failed, and adds of another date. */
    private fun prunePendingAdds() {
        val selected = localDate(_selectedHomeDate.value)
        val day = selectedDay.value
        pendingAdds.removeAll { p ->
            if (p.date != selected) return@removeAll true
            if (!p.clientId.isCompleted) return@removeAll false
            @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
            val cid = p.clientId.getCompleted() ?: return@removeAll true
            day != null && day.meals[p.meal].orEmpty().any { it.clientId == cid }
        }
    }

    fun addFoodToMeal(mealName: String, food: LoggedFood) {
        addFoodToMealReturningIndex(mealName, food)
    }

    fun addFoodToMealReturningIndex(mealName: String, food: LoggedFood): Int {
        prunePendingAdds()
        val date = localDate(_selectedHomeDate.value)
        val index = currentMeal(mealName).size + pendingAdds.count { it.date == date && it.meal == mealName }
        val pending = PendingAdd(date, mealName, index)
        pendingAdds += pending
        launchWrite {
            try {
                pending.clientId.complete(addLog(date, mealName, food, sortKey = null))
            } finally {
                pending.clientId.complete(null)   // no-op after success
            }
        }
        return index
    }

    /** Undo of [removeFoodFromMeal]: logs the same food again (new clientId) at its old position. */
    fun restoreRemovedFood(mealName: String, index: Int, food: LoggedFood) {
        if (food.clientId != null && currentMeal(mealName).any { it.clientId == food.clientId }) return
        val date = localDate(_selectedHomeDate.value)
        launchWrite { addLog(date, mealName, food, sortKey = food.sortKey) }
    }

    private suspend fun addLog(date: LocalDate, mealName: String, food: LoggedFood, sortKey: Long?): String {
        val clean = food.copy(id = null, clientId = null, pending = false, sortKey = null)
        val clientId = journal.addLog(date, mealName, clean, sortKey)
        _foodLogsChanged.tryEmit(Unit)
        // Hint local pentru worker-ul de remindere — sursa de adevăr rămâne backend-ul.
        runCatching { mealLogTracker.markLogged(date, mealName) }
        return clientId
    }

    /** After a local copy the mirror already emits; only the reminder hints are needed. */
    fun onCopied(dateKey: String, meals: Set<String>) {
        val date = LocalDate.parse(dateKey)
        _foodLogsChanged.tryEmit(Unit)
        viewModelScope.launch {
            meals.forEach { meal -> runCatching { mealLogTracker.markLogged(date, meal) } }
        }
    }

    /** Undoes a copy: deletes the copied rows locally; the queue removes them on the server. */
    fun undoCopy(dateKey: String, clientIds: List<String>) {
        if (clientIds.isEmpty()) return
        launchWrite {
            journal.deleteLogs(clientIds)
            _foodLogsChanged.tryEmit(Unit)
        }
    }

    fun removeFoodFromMeal(mealName: String, index: Int) {
        prunePendingAdds()
        val date = localDate(_selectedHomeDate.value)
        val pending = pendingAdds.firstOrNull { it.date == date && it.meal == mealName && it.index == index }
        if (pending != null) {
            // Undo of an add the mirror doesn't show yet: delete it by its clientId once written.
            pendingAdds -= pending
            deleteLog { pending.clientId.await() }
            return
        }
        val clientId = currentMeal(mealName).getOrNull(index)?.clientId ?: return
        deleteLog { clientId }
    }

    private fun deleteLog(clientId: suspend () -> String?) = launchWrite {
        val cid = clientId() ?: return@launchWrite
        journal.deleteLogs(listOf(cid))
        _foodLogsChanged.tryEmit(Unit)
    }

    fun updateFoodPortion(mealName: String, index: Int, grams: Int, unit: String?, unitQty: Float?) {
        val target = currentMeal(mealName).getOrNull(index) ?: return
        if (grams == target.grams && unit == target.unit && unitQty == target.unitQty) return  // no-op
        updateLog(target, target.copy(grams = grams, unit = unit, unitQty = unitQty))
    }

    /** Edit a recipe portion: totals are recomputed from the (possibly added/removed) ingredients. */
    fun updateRecipeLog(mealName: String, index: Int, ingredients: List<FoodIngredient>) {
        val target = currentMeal(mealName).getOrNull(index) ?: return
        if (ingredients.isEmpty()) return
        val totalG = ingredients.totalGrams()
        updateLog(target, target.copy(food = target.food.withIngredients(ingredients), grams = totalG.toInt()))
    }

    private fun updateLog(target: LoggedFood, updated: LoggedFood) {
        val clientId = target.clientId ?: return
        launchWrite {
            journal.updateLog(clientId, updated)
            _foodLogsChanged.tryEmit(Unit)
        }
    }

    private fun onSyncEvent(event: SyncEvent) {
        when (event) {
            is SyncEvent.Synced -> {
                _foodLogsChanged.tryEmit(Unit)
                _dayStatusChanged.tryEmit(Unit)
                // Goals depend on the server's latest weight: reload once the weight itself is synced
                // (another op of the same date may sync before it).
                val dates = event.dates.filter { it in weightDatesAwaitingSync }
                if (dates.isNotEmpty()) viewModelScope.launch {
                    val settled = dates.filterNot { journal.isWeightPending(LocalDate.parse(it)) }
                    if (settled.isNotEmpty()) {
                        weightDatesAwaitingSync.removeAll(settled.toSet())
                        loadGoals()
                    }
                }
            }
            SyncEvent.Rejected -> _toastEvents.tryEmit(ToastEvent(R.string.sync_rejected))
        }
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

    /**
     * Wipes every piece of per-user state so a different account that logs in on the
     * same process starts clean. The ViewModel is scoped to [MainActivity] and survives
     * logout. The journal itself lives in the mirror ([JournalRepository.clearAll] on logout);
     * here only UI state is reset.
     *
     * App-level state (theme/language/notifications in [appSettings]) is intentionally
     * left untouched — those are device preferences, not user data.
     */
    fun resetUserState() {
        _searchQuery.value = ""
        _lastLookupBarcode.value = null
        currentQuery = ""
        currentPage = 1
        _searchState.value = SearchUiState.Idle
        _showMealSelector.value = false
        _userProfile.value = UserProfile()
        lastAppliedAuthProfile = null
        _nutritionGoals.value = null
        hydrationSaveJob?.cancel()
        hydrationSaveJob = null
        hydrationPrefsBeforeEdit = null
        yesterdayRequestedFor = null
        _yesterdayAnsweredFor.value = null
        _loadingDay.value = false
        _selectedHomeDate.value = clock()
        pendingAdds.clear()
        weightDatesAwaitingSync.clear()
    }

    init {
        viewModelScope.launch { journal.events.collect(::onSyncEvent) }
        viewModelScope.launch { refreshDay(localDate(clock())) }
        loadYesterdayIfToday()
        loadGoals()
    }
}

private fun LocalDay?.hasFood(): Boolean = this != null && meals.values.any { it.isNotEmpty() }
