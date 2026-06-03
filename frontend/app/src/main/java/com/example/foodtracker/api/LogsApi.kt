package com.example.foodtracker.api

import com.example.foodtracker.model.DayResponseDto
import com.example.foodtracker.model.FoodLogCreateDto
import com.example.foodtracker.model.FoodLogDto
import com.example.foodtracker.model.FoodLogUpdateDto
import com.example.foodtracker.model.HydrationDto
import com.example.foodtracker.model.HydrationUpdateDto
import com.example.foodtracker.model.WeightCheckInCreateDto
import com.example.foodtracker.model.WeightCheckInDto
import com.example.foodtracker.model.WeightCheckInListDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface LogsApi {

    @GET("days/{date}")
    suspend fun getDay(@Path("date") date: String): DayResponseDto

    @POST("food-logs")
    suspend fun createFoodLog(@Body body: FoodLogCreateDto): FoodLogDto

    @PATCH("food-logs/{id}")
    suspend fun updateFoodLog(@Path("id") id: Long, @Body body: FoodLogUpdateDto): FoodLogDto

    @DELETE("food-logs/{id}")
    suspend fun deleteFoodLog(@Path("id") id: Long): Response<Unit>

    @PUT("hydration/{date}")
    suspend fun putHydration(
        @Path("date") date: String,
        @Body body: HydrationUpdateDto,
    ): HydrationDto

    @POST("weight-check-ins")
    suspend fun postWeightCheckIn(@Body body: WeightCheckInCreateDto): WeightCheckInDto

    @GET("weight-check-ins")
    suspend fun getWeightCheckIns(
        @Query("from") from: String,
        @Query("to") to: String,
    ): WeightCheckInListDto
}
