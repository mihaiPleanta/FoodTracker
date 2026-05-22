package com.example.foodtracker.model

data class NutritionGoals(
    val calorieGoal: Int = 2000,
    val proteinGoal: Int = 150,
    val carbsGoal: Int = 250,
    val fatGoal: Int = 65,
    val mode: GoalsMode = GoalsMode.MAINTENANCE,
)

enum class GoalsMode { DEFICIT, MAINTENANCE, SURPLUS }
