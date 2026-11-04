package com.example.foodtracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.foodtracker.api.RetrofitInstance
import com.example.foodtracker.api.StreakApi
import com.example.foodtracker.data.StreakSnapshot
import com.example.foodtracker.data.StreakSnapshotStorage
import com.example.foodtracker.model.RecoveryOfferDto
import com.example.foodtracker.model.StreakResponseDto
import com.example.foodtracker.model.StreakTodayDto
import com.example.foodtracker.streak.crossedMilestone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate

sealed interface StreakUiState {
    data object Loading : StreakUiState
    data class Loaded(val data: StreakResponseDto) : StreakUiState
    data class Offline(val current: Int, val todayLogged: Boolean) : StreakUiState
}

class StreakViewModel(
    private val api: StreakApi = RetrofitInstance.retrofit.create(StreakApi::class.java),
    private val snapshots: StreakSnapshotStorage,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val _state = MutableStateFlow<StreakUiState>(StreakUiState.Loading)
    val state: StateFlow<StreakUiState> = _state.asStateFlow()

    private val offerDismissed = MutableStateFlow(false)

    /** Recovery offer from the last response, unless dismissed since the last refresh. */
    val showOffer: StateFlow<RecoveryOfferDto?> = combine(_state, offerDismissed) { s, dismissed ->
        if (dismissed) null else (s as? StreakUiState.Loaded)?.data?.recoveryOffer
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Buffered channel (not a SharedFlow): food is usually logged from MealDetail while Home is
    // not composed, and the event must survive until the user returns to Home.
    private val _milestones = Channel<Int>(Channel.BUFFERED)
    val milestones: Flow<Int> = _milestones.receiveAsFlow()

    private var lastCurrent: Int? = null
    private var job: Job? = null

    fun refresh() {
        offerDismissed.value = false
        launchCall { api.getStreak(today().toString()) }
    }

    fun recover() = launchCall { api.recover(StreakTodayDto(today().toString())) }

    fun decline() = launchCall { api.decline(StreakTodayDto(today().toString())) }

    /** Hides the offer until the next refresh (e.g. from onResume). */
    fun dismissOffer() {
        offerDismissed.value = true
    }

    /** Logout / account deletion. */
    fun resetUserState() {
        job?.cancel()
        lastCurrent = null
        offerDismissed.value = false
        _state.value = StreakUiState.Loading
        viewModelScope.launch { snapshots.clear() }
    }

    private fun launchCall(call: suspend () -> StreakResponseDto) {
        job?.cancel()
        job = viewModelScope.launch {
            try {
                apply(call())
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                if (e.code() == 409) {
                    // Offer no longer valid server-side: re-sync.
                    try {
                        apply(api.getStreak(today().toString()))
                    } catch (e2: CancellationException) {
                        throw e2
                    } catch (_: Exception) {
                        fallBackToSnapshot()
                    }
                } else {
                    fallBackToSnapshot()
                }
            } catch (_: IOException) {
                fallBackToSnapshot()
            } catch (_: Exception) {
                fallBackToSnapshot()
            }
        }
    }

    private suspend fun apply(data: StreakResponseDto) {
        val now = today()
        val lastLogged = if (data.todayLogged) now
        else data.last14Days.filter { it.state == "logged" }.maxOfOrNull { LocalDate.parse(it.date) }
        snapshots.write(StreakSnapshot(data.current, now, lastLogged))
        crossedMilestone(lastCurrent, data.current)?.let { _milestones.trySend(it) }
        lastCurrent = data.current
        _state.value = StreakUiState.Loaded(data)
    }

    private suspend fun fallBackToSnapshot() {
        val snap = snapshots.read() ?: return
        _state.value = StreakUiState.Offline(snap.current, snap.lastLoggedDate == today())
    }
}
