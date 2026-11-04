package com.example.foodtracker.model

import com.google.gson.annotations.SerializedName

data class StreakDayDto(
    @SerializedName("date") val date: String,
    @SerializedName("state") val state: String,
)

data class RecoveryOfferDto(
    @SerializedName("previous_streak") val previousStreak: Int,
    @SerializedName("gap_start") val gapStart: String,
    @SerializedName("gap_end") val gapEnd: String,
)

data class StreakResponseDto(
    @SerializedName("current") val current: Int,
    @SerializedName("best") val best: Int,
    @SerializedName("total_logged_days") val totalLoggedDays: Int,
    @SerializedName("today_logged") val todayLogged: Boolean,
    @SerializedName("pause_used_this_week") val pauseUsedThisWeek: Boolean,
    @SerializedName("pause_used_on") val pauseUsedOn: String?,
    @SerializedName("recovery_available_this_month") val recoveryAvailableThisMonth: Boolean,
    @SerializedName("recovery_used_on") val recoveryUsedOn: String?,
    @SerializedName("last_14_days") val last14Days: List<StreakDayDto>,
    @SerializedName("next_milestone") val nextMilestone: Int,
    @SerializedName("recovery_offer") val recoveryOffer: RecoveryOfferDto?,
)

data class StreakTodayDto(@SerializedName("today") val today: String)
