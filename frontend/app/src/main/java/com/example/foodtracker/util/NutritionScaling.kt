package com.example.foodtracker.util

import com.example.foodtracker.model.FoodItem

data class ScaledNutrition(
    val kcal: Int,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
)

fun scaleNutrition(food: FoodItem, grams: Int): ScaledNutrition {
    val scale = grams / 100f
    return ScaledNutrition(
        kcal = (food.per100g * scale).toInt(),
        protein = food.protein100g * scale,
        carbs = food.carbs100g * scale,
        fat = food.fat100g * scale,
    )
}

fun parseGrams(input: String): Int? =
    input.toIntOrNull()?.takeIf { it in 1..2000 }
