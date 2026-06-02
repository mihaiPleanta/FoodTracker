package com.example.foodtracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.foodtracker.api.RetrofitInstance
import com.example.foodtracker.model.RecipeDto
import com.example.foodtracker.model.RecipeGenerateRequest
import com.example.foodtracker.model.SavedRecipeCreate
import com.example.foodtracker.model.SavedRecipeDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

sealed interface RecipeUiState {
    data object Idle : RecipeUiState
    data object Loading : RecipeUiState
    data class Result(val recipe: RecipeDto) : RecipeUiState
    data class Error(val message: String) : RecipeUiState
}

class RecipeViewModel : ViewModel() {

    private val api = RetrofitInstance.recipeApi

    private val _recipeState = MutableStateFlow<RecipeUiState>(RecipeUiState.Idle)
    val recipeState: StateFlow<RecipeUiState> = _recipeState.asStateFlow()

    private val _savedRecipes = MutableStateFlow<List<SavedRecipeDto>>(emptyList())
    val savedRecipes: StateFlow<List<SavedRecipeDto>> = _savedRecipes.asStateFlow()

    private val _selectedMealType = MutableStateFlow("BREAKFAST")
    val selectedMealType: StateFlow<String> = _selectedMealType.asStateFlow()

    init {
        loadSaved()
    }

    fun setMealType(mealType: String) {
        if (mealType == _selectedMealType.value) return
        _selectedMealType.value = mealType
        // A generated recipe belongs to the meal it was generated for; switching
        // meals should clear it so the card doesn't linger across meal types.
        _recipeState.value = RecipeUiState.Idle
    }

    fun generate() {
        _recipeState.value = RecipeUiState.Loading
        viewModelScope.launch {
            try {
                val recipe = api.generateRecipe(RecipeGenerateRequest(_selectedMealType.value))
                _recipeState.value = RecipeUiState.Result(recipe)
            } catch (e: HttpException) {
                _recipeState.value = RecipeUiState.Error(
                    when (e.code()) {
                        422 -> "Loghează mai multe alimente ca să generăm rețete"
                        503 -> "Serviciul AI nu e disponibil, încearcă din nou"
                        502 -> "Răspuns AI invalid, încearcă din nou"
                        else -> "Eroare neașteptată (${e.code()})"
                    }
                )
            } catch (e: IOException) {
                _recipeState.value = RecipeUiState.Error("Verifică conexiunea la internet")
            }
        }
    }

    fun saveCurrent() {
        val current = (_recipeState.value as? RecipeUiState.Result)?.recipe ?: return
        viewModelScope.launch {
            try {
                api.saveRecipe(SavedRecipeCreate(_selectedMealType.value, current))
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
