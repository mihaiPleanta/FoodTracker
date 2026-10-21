package com.example.foodtracker.api

import com.example.foodtracker.model.FoodItemDto
import com.example.foodtracker.model.RecentFoodDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface QuickPicksApi {
    @GET("foods/recent")
    suspend fun getRecent(@Query("meal") meal: String): List<RecentFoodDto>

    @GET("favorites")
    suspend fun getFavorites(): List<FoodItemDto>

    @PUT("favorites/{barcode}")
    suspend fun addFavorite(@Path("barcode") barcode: String, @Body food: FoodItemDto): FoodItemDto

    @DELETE("favorites/{barcode}")
    suspend fun removeFavorite(@Path("barcode") barcode: String): Response<Unit>
}
