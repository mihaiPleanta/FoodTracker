package com.example.foodtracker.model

// One component of a recipe portion: a name, its grams in the recipe, and its
// per-100g macros. Editing grams scales the contribution linearly.
data class FoodIngredient(
    val name: String,
    val grams: Float,
    val kcal100g: Float,
    val protein100g: Float,
    val carbs100g: Float,
    val fat100g: Float,
) {
    val kcal: Float get() = kcal100g * grams / 100f
    val protein: Float get() = protein100g * grams / 100f
    val carbs: Float get() = carbs100g * grams / 100f
    val fat: Float get() = fat100g * grams / 100f
}

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
    // Non-null ⇒ this is a recipe portion composed of these ingredients.
    val ingredients: List<FoodIngredient>? = null,
)

data class LoggedFood(
    val food: FoodItem,
    val grams: Int,
    val id: Long? = null,
    val clientTempId: String? = null,
) {
    val calories: Int get() = (food.per100g * grams / 100f).toInt()
    val protein:  Float get() = food.protein100g * grams / 100f
    val carbs:    Float get() = food.carbs100g * grams / 100f
    val fat:      Float get() = food.fat100g * grams / 100f
}
