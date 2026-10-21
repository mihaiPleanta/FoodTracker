package com.example.foodtracker.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.foodtracker.R
import com.example.foodtracker.api.QuickPicksApi
import com.example.foodtracker.api.RetrofitInstance
import com.example.foodtracker.model.FoodItem
import com.example.foodtracker.model.RecentFood
import com.example.foodtracker.model.mealKeyToBackend
import com.example.foodtracker.model.toDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

sealed interface QuickListState<out T> {
    data object Loading : QuickListState<Nothing>
    data class Loaded<T>(val items: List<T>) : QuickListState<T>
    data class Error(@StringRes val messageRes: Int) : QuickListState<Nothing>
}

/**
 * Recent + favorite foods shown on the meal screen before the user types a query.
 * Activity-scoped (like RecipeViewModel), so logout must call [resetUserState].
 */
class QuickPicksViewModel(
    private val api: QuickPicksApi = RetrofitInstance.quickPicksApi,
) : ViewModel() {

    private val _recent = MutableStateFlow<QuickListState<RecentFood>>(QuickListState.Loading)
    val recent: StateFlow<QuickListState<RecentFood>> = _recent.asStateFlow()

    private val _favorites = MutableStateFlow<QuickListState<FoodItem>>(QuickListState.Loading)
    val favorites: StateFlow<QuickListState<FoodItem>> = _favorites.asStateFlow()

    val favoriteBarcodes: StateFlow<Set<String>> = _favorites
        .map { state -> (state as? QuickListState.Loaded)?.items?.map { it.barcode }?.toSet() ?: emptySet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _toastEvents = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val toastEvents: SharedFlow<Int> = _toastEvents.asSharedFlow()

    private var lastMeal: String? = null

    /** Shows Loading first so a previous meal's / account's lists never flash. */
    fun load(meal: String) {
        lastMeal = meal
        _recent.value = QuickListState.Loading
        _favorites.value = QuickListState.Loading
        viewModelScope.launch { fetchRecent(meal) }
        viewModelScope.launch { fetchFavorites() }
    }

    /** Re-fetches recents for the last loaded meal without a Loading flash. */
    fun refreshRecent() {
        val meal = lastMeal ?: return
        viewModelScope.launch { fetchRecent(meal) }
    }

    fun toggleFavorite(food: FoodItem) {
        val previous = _favorites.value
        val current = (previous as? QuickListState.Loaded)?.items
        if (current == null) {
            _toastEvents.tryEmit(R.string.error_favorite_update)
            return
        }
        val wasFavorite = current.any { it.barcode == food.barcode }
        _favorites.value = QuickListState.Loaded(
            if (wasFavorite) current.filterNot { it.barcode == food.barcode } else listOf(food) + current
        )
        viewModelScope.launch {
            try {
                if (wasFavorite) {
                    val response = api.removeFavorite(food.barcode)
                    if (!response.isSuccessful) throw HttpException(response)
                } else {
                    api.addFavorite(food.barcode, food.toDto())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _favorites.value = previous
                _toastEvents.tryEmit(R.string.error_favorite_update)
            }
        }
    }

    fun resetUserState() {
        lastMeal = null
        _recent.value = QuickListState.Loading
        _favorites.value = QuickListState.Loading
    }

    private suspend fun fetchRecent(meal: String) {
        _recent.value = try {
            QuickListState.Loaded(api.getRecent(mealKeyToBackend(meal)).map { it.toDomain() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            QuickListState.Error(errorRes(e))
        }
    }

    private suspend fun fetchFavorites() {
        _favorites.value = try {
            QuickListState.Loaded(api.getFavorites().map { it.toDomain() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            QuickListState.Error(errorRes(e))
        }
    }

    @StringRes
    private fun errorRes(e: Exception): Int =
        if (e is IOException) R.string.error_no_internet else R.string.error_generic
}
