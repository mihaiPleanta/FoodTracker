package com.example.foodtracker.model

import com.google.gson.annotations.SerializedName

data class GoalsDto(
    @SerializedName("calorie_goal")   val calorieGoal: Int,
    @SerializedName("protein_goal_g") val proteinGoal: Int,
    @SerializedName("carbs_goal_g")   val carbsGoal: Int,
    @SerializedName("fat_goal_g")     val fatGoal: Int,
    val mode: String,
) {
    fun toDomain(): NutritionGoals = NutritionGoals(
        calorieGoal = calorieGoal,
        proteinGoal = proteinGoal,
        carbsGoal = carbsGoal,
        fatGoal = fatGoal,
        mode = runCatching { GoalsMode.valueOf(mode) }.getOrDefault(GoalsMode.MAINTENANCE),
    )
}
