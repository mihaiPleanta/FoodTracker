package com.example.foodtracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.foodtracker.model.Food
import com.example.foodtracker.model.MealType
import com.example.foodtracker.ui.FoodItem
import com.example.foodtracker.ui.LoggedFood
import com.example.foodtracker.ui.foodDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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

    // ── Legacy food list (keep for compatibility) ─────────────────────────────
    private val _foods = MutableStateFlow<List<Food>>(emptyList())
    val foods: StateFlow<List<Food>> = _foods

    // ── Meal selector pop-up state ────────────────────────────────────────────
    private val _showMealSelector = MutableStateFlow(false)
    val showMealSelector: StateFlow<Boolean> = _showMealSelector.asStateFlow()

    fun openMealSelector()  { _showMealSelector.value = true  }
    fun closeMealSelector() { _showMealSelector.value = false }
    fun toggleMealSelector() { _showMealSelector.value = !_showMealSelector.value }

    // ── Date-driven food history ───────────────────────────────────────────────
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun dateKey(date: Date): String = dateFormatter.format(date)

    private fun todayKey(): String = dateKey(Date())

    private fun seedHistory(): Map<String, DayMeals> {
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val twoDaysAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -2) }

        return mapOf(
            dateKey(today.time) to DayMeals(
                breakfast = listOf(
                    LoggedFood(foodDatabase[0], 120),
                    LoggedFood(foodDatabase[1], 200),
                    LoggedFood(foodDatabase[2], 118),
                    LoggedFood(foodDatabase[3], 150)
                ),
                lunch = listOf(
                    LoggedFood(foodDatabase[4], 200),
                    LoggedFood(foodDatabase[5], 150),
                    LoggedFood(foodDatabase[10], 100)
                )
            ),
            dateKey(yesterday.time) to DayMeals(
                breakfast = listOf(
                    LoggedFood(foodDatabase[0], 100),
                    LoggedFood(foodDatabase[10], 180)
                ),
                lunch = listOf(
                    LoggedFood(foodDatabase[4], 180),
                    LoggedFood(foodDatabase[12], 140)
                ),
                dinner = listOf(
                    LoggedFood(foodDatabase[6], 160),
                    LoggedFood(foodDatabase[11], 200)
                )
            ),
            dateKey(twoDaysAgo.time) to DayMeals(
                breakfast = listOf(LoggedFood(foodDatabase[3], 180)),
                snacks = listOf(LoggedFood(foodDatabase[9], 30), LoggedFood(foodDatabase[10], 150))
            )
        )
    }

    private val _selectedHomeDate = MutableStateFlow(Date())
    val selectedHomeDate: StateFlow<Date> = _selectedHomeDate.asStateFlow()

    private val _foodsByDate = MutableStateFlow(seedHistory())

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

    fun addFood(food: Food) { _foods.value = _foods.value + food }
    fun getFoodsByMeal(mealType: MealType) = _foods.value.filter { it.mealType == mealType }
}