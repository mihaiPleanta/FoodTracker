package com.example.foodtracker.model

const val DEFAULT_HYDRATION_GOAL_L = 2.5f

data class NutritionGoals(
    val calorieGoal: Int = 2000,
    val proteinGoal: Int = 150,
    val carbsGoal: Int = 250,
    val fatGoal: Int = 65,
    val mode: GoalsMode = GoalsMode.MAINTENANCE,
    val hydration: HydrationGoal? = null,
    val tdee: Int? = null,
)

data class HydrationGoal(
    val goalL: Float,
    val autoL: Float,
    val isManual: Boolean,
    val weightPartL: Float,
    val activityBonusL: Float,
    val creatineBonusL: Float,
)

enum class GoalsMode { DEFICIT, MAINTENANCE, SURPLUS }
