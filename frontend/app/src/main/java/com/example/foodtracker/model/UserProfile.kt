package com.example.foodtracker.model

enum class Gender { MALE, FEMALE, OTHER }

enum class ActivityLevel { SEDENTARY, LIGHT, MODERATE, ACTIVE, VERY_ACTIVE }

data class UserProfile(
    val name: String = "Mihai",
    val age: Int = 22,
    val gender: Gender = Gender.MALE,
    val heightCm: Int = 178,
    val currentWeightKg: Float = 78.1f,
    val targetWeightKg: Float = 75.0f,
    val activityLevel: ActivityLevel = ActivityLevel.MODERATE,
    val takesCreatine: Boolean = false,
    val hydrationGoalOverrideL: Float? = null
)

fun UserProfile.toProfileDto() = ProfileDto(
    name = name,
    age = age,
    gender = gender.name,
    heightCm = heightCm,
    currentWeightKg = currentWeightKg,
    targetWeightKg = targetWeightKg,
    activityLevel = activityLevel.name,
    takesCreatine = takesCreatine,
    hydrationGoalOverrideL = hydrationGoalOverrideL
)

fun ProfileDto.toUserProfile() = UserProfile(
    name = name,
    age = age,
    gender = runCatching { Gender.valueOf(gender) }.getOrDefault(Gender.OTHER),
    heightCm = heightCm,
    currentWeightKg = currentWeightKg,
    targetWeightKg = targetWeightKg,
    activityLevel = runCatching { ActivityLevel.valueOf(activityLevel) }
        .getOrDefault(ActivityLevel.MODERATE),
    takesCreatine = takesCreatine,
    hydrationGoalOverrideL = hydrationGoalOverrideL
)
