package com.example.foodtracker.model

import com.google.gson.annotations.SerializedName

data class FoodItemDto(
    @SerializedName("barcode")       val barcode: String,
    @SerializedName("name")          val name: String,
    @SerializedName("brand")         val brand: String?     = null,
    @SerializedName("image_url")     val imageUrl: String?  = null,
    @SerializedName("kcal_100g")     val kcal100g: Float,
    @SerializedName("protein_100g")  val protein100g: Float,
    @SerializedName("carbs_100g")    val carbs100g: Float,
    @SerializedName("fat_100g")      val fat100g: Float,
    @SerializedName("categories")    val categories: List<String> = emptyList(),
) {
    fun toDomain(): FoodItem = FoodItem(
        barcode     = barcode,
        name        = name,
        brand       = brand,
        imageUrl    = imageUrl,
        categories  = categories,
        per100g     = kcal100g.toInt(),
        protein100g = protein100g,
        carbs100g   = carbs100g,
        fat100g     = fat100g,
    )
}

data class SearchResponseDto(
    @SerializedName("items") val items: List<FoodItemDto>,
    @SerializedName("count") val count: Int,
)
