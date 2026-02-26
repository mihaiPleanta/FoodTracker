package com.example.foodtracker.api

import com.example.foodtracker.model.Food
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface FoodApi {
    @GET("foods")
    suspend fun getFoods(): List<Food>

    @POST("foods")
    suspend fun addFood(@Body food: Food)
}
