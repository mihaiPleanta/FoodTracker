package com.example.foodtracker.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.example.foodtracker.model.Food
import com.example.foodtracker.model.MealType

class FoodViewModel : ViewModel() {

    private val _foods = MutableStateFlow<List<Food>>(emptyList())
    val foods: StateFlow<List<Food>> = _foods

    fun addFood(food: Food) {
        _foods.value = _foods.value + food
    }

    fun getTotalCalories(): Int =
        _foods.value.sumOf { it.calories }

    fun getTotalProtein(): Int =
        _foods.value.sumOf { it.protein }

    fun getTotalCarbs(): Int =
        _foods.value.sumOf { it.carbs }

    fun getTotalFat(): Int =
        _foods.value.sumOf { it.fat }

    fun getFoodsByMeal(mealType: MealType): List<Food> =
        _foods.value.filter { it.mealType == mealType }
}