package com.example.foodtracker.model

import com.google.gson.annotations.SerializedName

/** A previously logged food plus the grams to pre-fill when re-adding it. */
data class RecentFood(val food: FoodItem, val lastGrams: Int)

data class RecentFoodDto(
    @SerializedName("food")       val food: FoodItemDto,
    @SerializedName("last_grams") val lastGrams: Int,
) {
    fun toDomain(): RecentFood = RecentFood(food.toDomain(), lastGrams)
}

fun FoodItem.toDto(): FoodItemDto = FoodItemDto(
    barcode     = barcode,
    name        = name,
    brand       = brand,
    imageUrl    = imageUrl,
    kcal100g    = per100g.toFloat(),
    protein100g = protein100g,
    carbs100g   = carbs100g,
    fat100g     = fat100g,
    categories  = categories,
)
