package com.example.foodtracker.model

enum class MealType {
    BREAKFAST, LUNCH, DINNER, SNACK
}

data class Food(
    val name: String,
    val calories: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    val mealType: MealType
)