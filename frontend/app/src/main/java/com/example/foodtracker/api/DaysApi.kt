package com.example.foodtracker.api

import com.example.foodtracker.model.DayStatusResponseDto
import com.example.foodtracker.model.DayStatusUpdateDto
import com.example.foodtracker.model.StatsDaysResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface DaysApi {

    @PUT("days/{date}/status")
    suspend fun putStatus(@Path("date") date: String, @Body body: DayStatusUpdateDto): DayStatusResponseDto

    @DELETE("days/{date}/status")
    suspend fun deleteStatus(@Path("date") date: String): Response<Unit>

    @GET("stats/days")
    suspend fun getStatsDays(@Query("from") from: String, @Query("to") to: String): StatsDaysResponseDto
}
