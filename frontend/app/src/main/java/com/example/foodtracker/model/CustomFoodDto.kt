package com.example.foodtracker.model

import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import retrofit2.HttpException

data class ServingDto(
    @SerializedName("unit")  val unit: String,
    @SerializedName("grams") val grams: Float,
)

data class CustomFoodInDto(
    @SerializedName("name")         val name: String,
    @SerializedName("brand")        val brand: String? = null,
    @SerializedName("barcode")      val barcode: String? = null,
    @SerializedName("kcal_100g")    val kcal100g: Float,
    @SerializedName("protein_100g") val protein100g: Float,
    @SerializedName("carbs_100g")   val carbs100g: Float,
    @SerializedName("fat_100g")     val fat100g: Float,
    @SerializedName("serving")      val serving: ServingDto? = null,
)

data class CustomFoodDto(
    @SerializedName("id")           val id: Int,
    @SerializedName("key")          val key: String,
    @SerializedName("name")         val name: String,
    @SerializedName("brand")        val brand: String? = null,
    @SerializedName("barcode")      val barcode: String? = null,
    @SerializedName("kcal_100g")    val kcal100g: Float,
    @SerializedName("protein_100g") val protein100g: Float,
    @SerializedName("carbs_100g")   val carbs100g: Float,
    @SerializedName("fat_100g")     val fat100g: Float,
    @SerializedName("serving")      val serving: ServingDto? = null,
) {
    fun toFoodItem(): FoodItem = FoodItem(
        barcode     = key,
        name        = name,
        brand       = brand,
        per100g     = kcal100g.toInt(),
        protein100g = protein100g,
        carbs100g   = carbs100g,
        fat100g     = fat100g,
    )
}

/** `existing_id` din corpul 409 `{"detail":{"code":"barcode_taken",...}}`, altfel null. */
fun parseBarcodeTaken(e: HttpException): Int? {
    if (e.code() != 409) return null
    return try {
        val body = e.response()?.errorBody()?.string() ?: return null
        val detail = JsonParser().parse(body).asJsonObject.getAsJsonObject("detail")
        if (detail.get("code")?.getAsString() != "barcode_taken") null
        else detail.get("existing_id")?.getAsInt()
    } catch (_: Exception) {
        null
    }
}
