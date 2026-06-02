package com.example.foodtracker.model

import com.google.gson.annotations.SerializedName

data class RecipeIngredientDto(
    val name: String,
    val quantity: String,
)

data class RecipeDto(
    val title: String,
    val description: String,
    val ingredients: List<RecipeIngredientDto>,
    val steps: List<String>,
    val servings: Int,
    @SerializedName("kcal_per_serving") val kcalPerServing: Int,
    @SerializedName("protein_g") val proteinG: Int,
    @SerializedName("carbs_g") val carbsG: Int,
    @SerializedName("fat_g") val fatG: Int,
)

data class RecipeGenerateRequest(
    @SerializedName("meal_type") val mealType: String,
)

data class SavedRecipeCreate(
    @SerializedName("meal_type") val mealType: String,
    val recipe: RecipeDto,
)

data class SavedRecipeDto(
    val id: Long,
    @SerializedName("meal_type") val mealType: String,
    val title: String,
    val recipe: RecipeDto,
    @SerializedName("created_at") val createdAt: String,
)
