package com.example.foodtracker.model

import com.google.gson.annotations.SerializedName

data class FoodLogUpdateDto(
    val grams: Int,
)

data class FoodLogCreateDto(
    @SerializedName("log_date") val logDate: String,    // "yyyy-MM-dd"
    val meal: String,                                   // BREAKFAST/LUNCH/DINNER/SNACKS
    val grams: Int,
    val barcode: String,
    val name: String,
    val brand: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    val categories: List<String> = emptyList(),
    @SerializedName("kcal_100g") val kcal100g: Float,
    @SerializedName("protein_100g") val protein100g: Float,
    @SerializedName("carbs_100g") val carbs100g: Float,
    @SerializedName("fat_100g") val fat100g: Float,
)

data class FoodLogDto(
    val id: Long,
    @SerializedName("log_date") val logDate: String,
    val meal: String,
    val grams: Int,
    val barcode: String,
    val name: String,
    val brand: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    val categories: List<String> = emptyList(),
    @SerializedName("kcal_100g") val kcal100g: Float,
    @SerializedName("protein_100g") val protein100g: Float,
    @SerializedName("carbs_100g") val carbs100g: Float,
    @SerializedName("fat_100g") val fat100g: Float,
) {
    fun toLogged(): LoggedFood = LoggedFood(
        food = FoodItem(
            barcode = barcode,
            name = name,
            brand = brand,
            imageUrl = imageUrl,
            categories = categories,
            per100g = kcal100g.toInt(),
            protein100g = protein100g,
            carbs100g = carbs100g,
            fat100g = fat100g,
        ),
        grams = grams,
        id = id,
        clientTempId = null,
    )
}

data class HydrationUpdateDto(val liters: Float)
data class HydrationDto(val date: String, val liters: Float)

data class WeightCheckInCreateDto(
    val date: String,
    @SerializedName("weight_kg") val weightKg: Float,
)

data class WeightCheckInDto(
    val date: String,
    @SerializedName("weight_kg") val weightKg: Float,
)

data class WeightCheckInListDto(val items: List<WeightCheckInDto>)

data class DayResponseDto(
    @SerializedName("foods_by_meal") val foodsByMeal: Map<String, List<FoodLogDto>>,
    @SerializedName("hydration_liters") val hydrationLiters: Float,
    @SerializedName("weight_check_in") val weightCheckIn: WeightCheckInDto?,
)
