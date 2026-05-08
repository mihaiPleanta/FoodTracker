package com.example.foodtracker.model

import com.google.gson.annotations.SerializedName

data class ProfileDto(
    @SerializedName("name")               val name: String,
    @SerializedName("age")                val age: Int,
    @SerializedName("gender")             val gender: String,
    @SerializedName("height_cm")          val heightCm: Int,
    @SerializedName("current_weight_kg")  val currentWeightKg: Float,
    @SerializedName("target_weight_kg")   val targetWeightKg: Float,
    @SerializedName("activity_level")     val activityLevel: String
)

data class TdeeResponseDto(
    @SerializedName("calorie_goal") val calorieGoal: Int
)
