package com.example.foodtracker.api

import com.example.foodtracker.model.FoodItemDto
import com.example.foodtracker.model.SearchResponseDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface FoodApi {
    @GET("foods/search")
    suspend fun searchFoods(
        @Query("q") query: String,
        @Query("page_size") pageSize: Int = 20,
    ): SearchResponseDto

    @GET("foods/barcode/{barcode}")
    suspend fun getFoodByBarcode(@Path("barcode") barcode: String): FoodItemDto
}
