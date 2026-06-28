package com.example.foodtracker.api

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitInstance {
    // Adresa backend-ului FastAPI.
    //  - Emulator Android:        "http://10.0.2.2:8000/"  (alias pt. localhost-ul gazdei)
    //  - Tabletă/telefon fizic:   "http://<IP-ul-LAN-al-laptopului>:8000/" pe aceeași rețea Wi-Fi
    // Valoarea activă e pentru demoul pe tabletă fizică; dacă schimbi rețeaua/hotspot-ul,
    // actualizează IP-ul (pe macOS: `ipconfig getifaddr en0`). Pentru emulator, pune înapoi 10.0.2.2.
    private const val BASE_URL = "http://192.168.1.130:8000/"

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
