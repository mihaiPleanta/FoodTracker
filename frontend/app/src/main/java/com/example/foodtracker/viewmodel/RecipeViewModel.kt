package com.example.foodtracker.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.foodtracker.R
import com.example.foodtracker.api.RetrofitInstance
import com.example.foodtracker.model.RecipeDto
import com.example.foodtracker.model.RecipeGenerateRequest
import com.example.foodtracker.model.SavedRecipeCreate
import com.example.foodtracker.model.SavedRecipeDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

sealed interface RecipeUiState {
    data object Idle : RecipeUiState
    data object Loading : RecipeUiState
    data class Result(val recipe: RecipeDto) : RecipeUiState
    data class Error(@StringRes val messageRes: Int, val arg: Int? = null) : RecipeUiState
}

class RecipeViewModel : ViewModel() {

    private val api = RetrofitInstance.recipeApi

    // Generated recipe is kept per meal type, so switching meals preserves each
    // meal's last result instead of discarding it.
    private val _recipeStates = MutableStateFlow<Map<String, RecipeUiState>>(emptyMap())

    private val _savedRecipes = MutableStateFlow<List<SavedRecipeDto>>(emptyList())
    val savedRecipes: StateFlow<List<SavedRecipeDto>> = _savedRecipes.asStateFlow()

    private val _selectedMealType = MutableStateFlow("BREAKFAST")
    val selectedMealType: StateFlow<String> = _selectedMealType.asStateFlow()

    // The state the screen renders: whatever was last produced for the selected meal.
    val recipeState: StateFlow<RecipeUiState> =
        combine(_selectedMealType, _recipeStates) { meal, states ->
            states[meal] ?: RecipeUiState.Idle
        }.stateIn(viewModelScope, SharingStarted.Eagerly, RecipeUiState.Idle)

    init {
        loadSaved()
    }

    fun setMealType(mealType: String) {
        _selectedMealType.value = mealType
    }

    private fun setState(meal: String, state: RecipeUiState) {
        _recipeStates.value = _recipeStates.value + (meal to state)
    }

    fun generate(language: String = "ro") {
        // Capture the meal at request time so a mid-flight meal switch lands the
        // result on the meal it was generated for.
        val meal = _selectedMealType.value
        setState(meal, RecipeUiState.Loading)
        viewModelScope.launch {
            try {
                val recipe = api.generateRecipe(RecipeGenerateRequest(meal, language))
                setState(meal, RecipeUiState.Result(recipe))
            } catch (e: HttpException) {
                setState(meal, when (e.code()) {
                    422 -> RecipeUiState.Error(R.string.error_recipe_insufficient)
                    503 -> RecipeUiState.Error(R.string.error_ai_unavailable)
                    502 -> RecipeUiState.Error(R.string.error_ai_invalid)
                    else -> RecipeUiState.Error(R.string.error_unexpected_code, e.code())
                })
            } catch (e: IOException) {
                setState(meal, RecipeUiState.Error(R.string.error_no_internet))
            }
        }
    }

    fun saveCurrent() {
        val meal = _selectedMealType.value
        val current = (_recipeStates.value[meal] as? RecipeUiState.Result)?.recipe ?: return
        viewModelScope.launch {
            try {
                api.saveRecipe(SavedRecipeCreate(meal, current))
                loadSaved()
            } catch (_: Exception) {
                // best-effort save; keep current recipe on screen
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            try {
                api.deleteRecipe(id)
                _savedRecipes.value = _savedRecipes.value.filterNot { it.id == id }
            } catch (_: Exception) {
            }
        }
    }

    fun loadSaved() {
        viewModelScope.launch {
            try {
                _savedRecipes.value = api.getRecipes()
            } catch (_: Exception) {
                // leave existing list; saved recipes are non-critical on load
            }
        }
    }
}
