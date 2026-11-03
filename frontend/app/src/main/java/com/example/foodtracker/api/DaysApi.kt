package com.example.foodtracker.api

import com.example.foodtracker.model.DayStatusResponseDto
import com.example.foodtracker.model.DayStatusUpdateDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.PUT
import retrofit2.http.Path

interface DaysApi {

    @PUT("days/{date}/status")
    suspend fun putStatus(@Path("date") date: String, @Body body: DayStatusUpdateDto): DayStatusResponseDto

    @DELETE("days/{date}/status")
    suspend fun deleteStatus(@Path("date") date: String): Response<Unit>
}
