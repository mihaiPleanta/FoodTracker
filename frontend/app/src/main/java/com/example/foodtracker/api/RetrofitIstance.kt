package com.example.foodtracker.api

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitInstance {
    private const val BASE_URL = "http://10.0.2.2:8000/"

    // Read/call timeouts are generous because /recipes/generate runs a local
    // Ollama model (10-40s). OkHttp's 10s default would otherwise abort it and
    // surface as an IOException ("Verifică conexiunea la internet").
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(200, TimeUnit.SECONDS)
        .build()

    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // Backward-compatible — FoodApi continues to work
    val api: FoodApi by lazy { retrofit.create(FoodApi::class.java) }

    val logsApi: LogsApi by lazy { retrofit.create(LogsApi::class.java) }
    val recipeApi: RecipeApi by lazy { retrofit.create(RecipeApi::class.java) }
}
