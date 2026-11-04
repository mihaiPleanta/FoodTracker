package com.example.foodtracker.api

import com.example.foodtracker.model.StreakResponseDto
import com.example.foodtracker.model.StreakTodayDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface StreakApi {
    @GET("streak")
    suspend fun getStreak(@Query("today") today: String): StreakResponseDto

    @POST("streak/recover")
    suspend fun recover(@Body body: StreakTodayDto): StreakResponseDto

    @POST("streak/decline")
    suspend fun decline(@Body body: StreakTodayDto): StreakResponseDto
}
