package com.example.foodtracker.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import com.example.foodtracker.model.Food
import com.example.foodtracker.model.MealType
import com.example.foodtracker.ui.FoodItem
import com.example.foodtracker.ui.LoggedFood
import com.example.foodtracker.ui.MealOption
import com.example.foodtracker.ui.foodDatabase
import com.example.foodtracker.ui.mealOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FoodViewModel : ViewModel() {

    // ── Legacy food list (keep for compatibility) ─────────────────────────────
    private val _foods = MutableStateFlow<List<Food>>(emptyList())
    val foods: StateFlow<List<Food>> = _foods

    // ── Meal selector pop-up state ────────────────────────────────────────────
    private val _showMealSelector = MutableStateFlow(false)
    val showMealSelector: StateFlow<Boolean> = _showMealSelector.asStateFlow()

    fun openMealSelector()  { _showMealSelector.value = true  }
    fun closeMealSelector() { _showMealSelector.value = false }
    fun toggleMealSelector() { _showMealSelector.value = !_showMealSelector.value }

    // ── Per-meal logged foods ─────────────────────────────────────────────────
    private val _breakfastFoods = MutableStateFlow(
        listOf(
            LoggedFood(foodDatabase[0], 120),  // Boiled Eggs
            LoggedFood(foodDatabase[1], 200),  // Oatmeal
            LoggedFood(foodDatabase[2], 118),  // Banana
            LoggedFood(foodDatabase[3], 150),  // Greek Yogurt
        )
    )
    private val _lunchFoods = MutableStateFlow(
        listOf(
            LoggedFood(foodDatabase[4], 200),  // Grilled Chicken
            LoggedFood(foodDatabase[5], 150),  // Brown Rice
            LoggedFood(foodDatabase[10], 100), // Apple
        )
    )
    private val _dinnerFoods  = MutableStateFlow<List<LoggedFood>>(emptyList())
    private val _snacksFoods  = MutableStateFlow<List<LoggedFood>>(emptyList())

    val breakfastFoods: StateFlow<List<LoggedFood>> = _breakfastFoods.asStateFlow()
    val lunchFoods:     StateFlow<List<LoggedFood>> = _lunchFoods.asStateFlow()
    val dinnerFoods:    StateFlow<List<LoggedFood>> = _dinnerFoods.asStateFlow()
    val snacksFoods:    StateFlow<List<LoggedFood>> = _snacksFoods.asStateFlow()

    fun getFoodsFlow(mealName: String): StateFlow<List<LoggedFood>> = when (mealName) {
        "Breakfast" -> _breakfastFoods
        "Lunch"     -> _lunchFoods
        "Dinner"    -> _dinnerFoods
        "Snacks"    -> _snacksFoods
        else        -> _breakfastFoods
    }

    fun addFoodToMeal(mealName: String, food: LoggedFood) {
        val flow = getMutableFlow(mealName)
        flow.value = flow.value + food
    }

    fun removeFoodFromMeal(mealName: String, index: Int) {
        val flow = getMutableFlow(mealName)
        flow.value = flow.value.toMutableList().also { it.removeAt(index) }
    }

    private fun getMutableFlow(mealName: String): MutableStateFlow<List<LoggedFood>> = when (mealName) {
        "Breakfast" -> _breakfastFoods
        "Lunch"     -> _lunchFoods
        "Dinner"    -> _dinnerFoods
        "Snacks"    -> _snacksFoods
        else        -> _breakfastFoods
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

    fun addFood(food: Food) { _foods.value = _foods.value + food }
    fun getFoodsByMeal(mealType: MealType) = _foods.value.filter { it.mealType == mealType }
}