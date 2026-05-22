package com.example.foodtracker.api

import com.example.foodtracker.model.GoalsDto
import com.example.foodtracker.model.ProfileDto
import com.example.foodtracker.model.TdeeResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ProfileApi {
    @GET("profile")
    suspend fun getProfile(): Response<ProfileDto>

    @POST("profile")
    suspend fun saveProfile(@Body profile: ProfileDto): Response<ProfileDto>

    @POST("calculate-tdee")
    suspend fun calculateTdee(@Body profile: ProfileDto): Response<TdeeResponseDto>

    @GET("goals")
    suspend fun getGoals(): Response<GoalsDto>
}
