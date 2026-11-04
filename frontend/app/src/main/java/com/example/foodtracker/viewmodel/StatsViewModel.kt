package com.example.foodtracker.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.foodtracker.R
import com.example.foodtracker.model.StatsDaysResponseDto
import com.example.foodtracker.stats.PeriodDeltas
import com.example.foodtracker.stats.PeriodKind
import com.example.foodtracker.stats.PeriodStatsCalculator
import com.example.foodtracker.stats.PeriodSummary
import com.example.foodtracker.stats.StatsPeriod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate

enum class StatsMode { DAY, WEEK, MONTH }

sealed interface PeriodUiState {
    data object Loading : PeriodUiState
    data class Loaded(
        val period: StatsPeriod,
        val summary: PeriodSummary,
        val deltas: PeriodDeltas,
        val canGoNext: Boolean,
        /** The CURRENT period's response (not the previous one). */
        val raw: StatsDaysResponseDto,
    ) : PeriodUiState
    data class Error(@StringRes val message: Int) : PeriodUiState
}

/** Where period stats come from (local mirror first, server as fallback). */
fun interface StatsSource {
    suspend fun days(from: LocalDate, to: LocalDate): StatsDaysResponseDto
}

class StatsViewModel(
    private val source: StatsSource,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val _mode = MutableStateFlow(StatsMode.DAY)
    val mode: StateFlow<StatsMode> = _mode.asStateFlow()

    private val _state = MutableStateFlow<PeriodUiState>(PeriodUiState.Loading)
    val state: StateFlow<PeriodUiState> = _state.asStateFlow()

    private val cache = mutableMapOf<StatsPeriod, StatsDaysResponseDto>()
    private var currentPeriod: StatsPeriod? = null
    private var loadJob: Job? = null
    private var tolerancePct = 10
    private var hydrationGoalL = 2.5f

    fun setMode(mode: StatsMode, anchor: LocalDate) {
        // Coming back from DAY: logs may have changed meanwhile, so start from a fresh cache.
        if (_mode.value == StatsMode.DAY && mode != StatsMode.DAY) cache.clear()
        _mode.value = mode
        when (mode) {
            StatsMode.DAY -> loadJob?.cancel()
            StatsMode.WEEK -> load(StatsPeriod.containing(PeriodKind.WEEK, anchor))
            StatsMode.MONTH -> load(StatsPeriod.containing(PeriodKind.MONTH, anchor))
        }
    }

    fun previous() {
        currentPeriod?.takeIf { _mode.value != StatsMode.DAY }?.let { load(it.previous()) }
    }

    fun next() {
        val p = currentPeriod?.takeIf { _mode.value != StatsMode.DAY } ?: return
        if (p.isCurrent(today())) return
        load(p.next())
    }

    fun retry() {
        currentPeriod?.takeIf { _mode.value != StatsMode.DAY }?.let { load(it) }
    }

    /** Day statuses/logs changed: drop cached data and reload what is shown. */
    fun invalidate() {
        cache.clear()
        retry()
    }

    /** Logout / account deletion: drop all per-account data. */
    fun resetUserState() {
        loadJob?.cancel()
        cache.clear()
        currentPeriod = null
        _mode.value = StatsMode.DAY
        _state.value = PeriodUiState.Loading
    }

    fun setTolerance(pct: Int) {
        if (pct == tolerancePct) return
        tolerancePct = pct
        republish()
    }

    fun setHydrationGoal(liters: Float) {
        if (liters == hydrationGoalL) return
        hydrationGoalL = liters
        republish()
    }

    private fun load(period: StatsPeriod) {
        loadJob?.cancel()
        currentPeriod = period
        val cached = cache[period]
        val cachedPrev = cache[period.previous()]
        if (cached != null && cachedPrev != null) {
            publish(period, cached, cachedPrev)
            return
        }
        _state.value = PeriodUiState.Loading
        loadJob = viewModelScope.launch {
            try {
                val (cur, prev) = coroutineScope {
                    val a = async { fetch(period) }
                    val b = async { fetch(period.previous()) }
                    a.await() to b.await()
                }
                publish(period, cur, prev)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                _state.value = PeriodUiState.Error(R.string.error_no_internet)
            } catch (e: Exception) {
                _state.value = PeriodUiState.Error(R.string.error_load_stats)
            }
        }
    }

    private suspend fun fetch(period: StatsPeriod): StatsDaysResponseDto {
        cache[period]?.let { return it }
        return source.days(period.start, period.end)
            .also { cache[period] = it }
    }

    private fun republish() {
        val p = currentPeriod ?: return
        if (_state.value !is PeriodUiState.Loaded) return
        val cur = cache[p] ?: return
        val prev = cache[p.previous()] ?: return
        publish(p, cur, prev)
    }

    private fun publish(period: StatsPeriod, cur: StatsDaysResponseDto, prev: StatsDaysResponseDto) {
        val now = today()
        val summary = PeriodStatsCalculator.summarize(period, cur.days, tolerancePct, hydrationGoalL, now)
        val prevSummary = PeriodStatsCalculator.summarize(period.previous(), prev.days, tolerancePct, hydrationGoalL, now)
        _state.value = PeriodUiState.Loaded(
            period = period,
            summary = summary,
            deltas = PeriodStatsCalculator.deltas(summary, prevSummary),
            canGoNext = !period.isCurrent(now),
            raw = cur,
        )
    }
}
