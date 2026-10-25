package com.example.foodtracker.model

import com.google.gson.annotations.SerializedName

data class GoalsDto(
    @SerializedName("calorie_goal")   val calorieGoal: Int,
    @SerializedName("protein_goal_g") val proteinGoal: Int,
    @SerializedName("carbs_goal_g")   val carbsGoal: Int,
    @SerializedName("fat_goal_g")     val fatGoal: Int,
    val mode: String,
    val hydration: HydrationGoalDto? = null,
) {
    fun toDomain(): NutritionGoals = NutritionGoals(
        calorieGoal = calorieGoal,
        proteinGoal = proteinGoal,
        carbsGoal = carbsGoal,
        fatGoal = fatGoal,
        mode = runCatching { GoalsMode.valueOf(mode) }.getOrDefault(GoalsMode.MAINTENANCE),
        hydration = hydration?.toDomain(),
    )
}

data class HydrationGoalDto(
    @SerializedName("goal_l")           val goalL: Float,
    @SerializedName("auto_l")           val autoL: Float,
    @SerializedName("is_manual")        val isManual: Boolean,
    @SerializedName("weight_part_l")    val weightPartL: Float,
    @SerializedName("activity_bonus_l") val activityBonusL: Float,
    @SerializedName("creatine_bonus_l") val creatineBonusL: Float,
) {
    fun toDomain() = HydrationGoal(goalL, autoL, isManual, weightPartL, activityBonusL, creatineBonusL)
}
