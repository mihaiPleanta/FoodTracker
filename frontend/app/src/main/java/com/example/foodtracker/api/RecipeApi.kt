package com.example.foodtracker.api

import com.example.foodtracker.model.RecipeDto
import com.example.foodtracker.model.RecipeGenerateRequest
import com.example.foodtracker.model.SavedRecipeCreate
import com.example.foodtracker.model.SavedRecipeDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface RecipeApi {

    @POST("recipes/generate")
    suspend fun generateRecipe(@Body body: RecipeGenerateRequest): RecipeDto

    @POST("recipes")
    suspend fun saveRecipe(@Body body: SavedRecipeCreate): SavedRecipeDto

    @GET("recipes")
    suspend fun getRecipes(): List<SavedRecipeDto>

    @DELETE("recipes/{id}")
    suspend fun deleteRecipe(@Path("id") id: Long): Response<Unit>
}
