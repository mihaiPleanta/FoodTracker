package com.example.foodtracker.api

import com.example.foodtracker.model.CustomFoodDto
import com.example.foodtracker.model.CustomFoodInDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface CustomFoodsApi {
    @GET("custom-foods")
    suspend fun list(): List<CustomFoodDto>

    @POST("custom-foods")
    suspend fun create(@Body body: CustomFoodInDto): CustomFoodDto

    @PUT("custom-foods/{id}")
    suspend fun update(@Path("id") id: Int, @Body body: CustomFoodInDto): CustomFoodDto

    @DELETE("custom-foods/{id}")
    suspend fun delete(@Path("id") id: Int): Response<Unit>
}
