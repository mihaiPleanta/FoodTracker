package com.example.foodtracker.model

data class FoodItem(
    val barcode: String,
    val name: String,
    val brand: String? = null,
    val imageUrl: String? = null,
    val categories: List<String> = emptyList(),
    val per100g: Int,         // kcal
    val protein100g: Float,
    val carbs100g: Float,
    val fat100g: Float,
)

data class LoggedFood(
    val food: FoodItem,
    val grams: Int,
) {
    val calories: Int get() = (food.per100g * grams / 100f).toInt()
    val protein:  Float get() = food.protein100g * grams / 100f
    val carbs:    Float get() = food.carbs100g * grams / 100f
    val fat:      Float get() = food.fat100g * grams / 100f
}
