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

/** Sum of ingredient grams, floored at 1 so per-100g division is always defined. */
fun List<FoodIngredient>.totalGrams(): Double =
    sumOf { it.grams.toDouble() }.coerceAtLeast(1.0)

/** Single formula for a recipe portion: per-100g macros recomputed from [ingredients]
 *  so that grams * per100g / 100 == sum of ingredient contributions. */
fun FoodItem.withIngredients(ingredients: List<FoodIngredient>): FoodItem {
    val totalG = ingredients.totalGrams()
    return copy(
        per100g = (ingredients.sumOf { it.kcal.toDouble() } / totalG * 100).toInt(),
        protein100g = (ingredients.sumOf { it.protein.toDouble() } / totalG * 100).toFloat(),
        carbs100g = (ingredients.sumOf { it.carbs.toDouble() } / totalG * 100).toFloat(),
        fat100g = (ingredients.sumOf { it.fat.toDouble() } / totalG * 100).toFloat(),
        ingredients = ingredients,
    )
}

data class LoggedFood(
    val food: FoodItem,
    val grams: Int,
    val id: Long? = null,
    val clientTempId: String? = null,
    val unit: String? = null,
    val unitQty: Float? = null,
) {
    val calories: Int get() = (food.per100g * grams / 100f).toInt()
    val protein:  Float get() = food.protein100g * grams / 100f
    val carbs:    Float get() = food.carbs100g * grams / 100f
    val fat:      Float get() = food.fat100g * grams / 100f
}
