package com.example.foodtracker.model

import com.google.gson.annotations.SerializedName

data class StatsGoalsDto(
    @SerializedName("calorie_goal") val calorieGoal: Int,
    @SerializedName("protein_goal_g") val proteinGoalG: Int,
    @SerializedName("carbs_goal_g") val carbsGoalG: Int,
    @SerializedName("fat_goal_g") val fatGoalG: Int,
    val tdee: Int,
)

data class StatsDayDto(
    val date: String,                                   // "yyyy-MM-dd"
    val kcal: Int,
    @SerializedName("protein_g") val proteinG: Float,
    @SerializedName("carbs_g") val carbsG: Float,
    @SerializedName("fat_g") val fatG: Float,
    @SerializedName("has_food") val hasFood: Boolean,
    val status: String? = null,                         // "complete" | "incomplete" | null
    val goals: StatsGoalsDto? = null,
    @SerializedName("hydration_l") val hydrationL: Float = 0f,
)

data class StatsWeightDto(
    val date: String,
    @SerializedName("weight_kg") val weightKg: Float,
)

data class TopFoodStatDto(
    val key: String,
    val name: String,
    val brand: String? = null,
    @SerializedName("total_kcal") val totalKcal: Int,
    @SerializedName("total_protein_g") val totalProteinG: Float,
    @SerializedName("days_count") val daysCount: Int,
)

data class TopFoodsDto(
    @SerializedName("by_kcal") val byKcal: List<TopFoodStatDto> = emptyList(),
    @SerializedName("by_protein") val byProtein: List<TopFoodStatDto> = emptyList(),
)

data class StatsDaysResponseDto(
    val days: List<StatsDayDto> = emptyList(),
    val weights: List<StatsWeightDto> = emptyList(),
    @SerializedName("top_foods") val topFoods: TopFoodsDto = TopFoodsDto(),
)
