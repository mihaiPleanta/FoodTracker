package com.example.foodtracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.foodtracker.R
import com.example.foodtracker.api.PortionsApi
import com.example.foodtracker.api.RetrofitInstance
import com.example.foodtracker.model.GramPresetsDto
import com.example.foodtracker.model.PortionUnitInDto
import com.example.foodtracker.model.Portions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException

sealed interface PortionsState {
    data object Idle : PortionsState
    data object Loading : PortionsState
    data class Loaded(val barcode: String, val portions: Portions) : PortionsState
    data object Failed : PortionsState
}

/**
 * Portion units and quick-gram presets of one product at a time.
 * Activity-scoped, so logout must call [resetUserState].
 */
class PortionsViewModel(
    private val api: PortionsApi = RetrofitInstance.portionsApi,
) : ViewModel() {

    private val _state = MutableStateFlow<PortionsState>(PortionsState.Idle)
    val state: StateFlow<PortionsState> = _state.asStateFlow()

    private val _toastEvents = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val toastEvents: SharedFlow<Int> = _toastEvents.asSharedFlow()

    private var loadJob: Job? = null
    private val writeJobs = mutableSetOf<Job>()
    private val writeMutex = Mutex()

    fun load(barcode: String) {
        loadJob?.cancel()
        _state.value = PortionsState.Loading
        loadJob = viewModelScope.launch {
            _state.value = try {
                PortionsState.Loaded(barcode, api.getPortions(barcode).toDomain())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PortionsState.Failed
            }
        }
    }

    fun saveUnit(barcode: String, unit: String, grams: Float) =
        write(barcode) { api.putUnit(barcode, PortionUnitInDto(unit, grams)) }

    fun deleteUnit(barcode: String, unit: String) =
        write(barcode) { requireSuccess(api.deleteUnit(barcode, unit)) }

    fun savePresets(barcode: String, grams: List<Int>) =
        write(barcode) { api.putPresets(barcode, GramPresetsDto(grams)) }

    fun resetPresets(barcode: String) =
        write(barcode) { requireSuccess(api.deletePresets(barcode)) }

    fun resetUserState() {
        loadJob?.cancel()
        writeJobs.toList().forEach { it.cancel() }
        writeJobs.clear()
        _state.value = PortionsState.Idle
    }

    /**
     * Runs [call], then reloads [barcode] without a Loading flash; on error the state is untouched.
     * Writes are serialized and never cancelled by later writes or [load]; only [resetUserState] cancels them.
     */
    private fun write(barcode: String, call: suspend () -> Unit) {
        lateinit var job: Job
        job = viewModelScope.launch {
            try {
                writeMutex.withLock { performWrite(barcode, call) }
            } finally {
                writeJobs.remove(job)
            }
        }
        writeJobs.add(job)
    }

    private suspend fun performWrite(barcode: String, call: suspend () -> Unit) {
        try {
            call()
            val fresh = api.getPortions(barcode).toDomain()
            val current = _state.value
            if (current is PortionsState.Loaded && current.barcode == barcode) {
                _state.value = PortionsState.Loaded(barcode, fresh)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _toastEvents.tryEmit(
                if (e is IOException) R.string.error_no_internet else R.string.error_save
            )
        }
    }

    private fun requireSuccess(response: retrofit2.Response<Unit>) {
        if (!response.isSuccessful) throw HttpException(response)
    }
}
