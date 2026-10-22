package com.example.foodtracker.api

import com.example.foodtracker.model.GramPresetsDto
import com.example.foodtracker.model.PortionUnitInDto
import com.example.foodtracker.model.PortionsDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface PortionsApi {
    @GET("portions/{barcode}")
    suspend fun getPortions(@Path("barcode") barcode: String): PortionsDto

    @PUT("portions/{barcode}/units")
    suspend fun putUnit(@Path("barcode") barcode: String, @Body body: PortionUnitInDto): PortionUnitInDto

    @DELETE("portions/{barcode}/units")
    suspend fun deleteUnit(@Path("barcode") barcode: String, @Query("unit") unit: String): Response<Unit>

    @PUT("portions/{barcode}/presets")
    suspend fun putPresets(@Path("barcode") barcode: String, @Body body: GramPresetsDto): GramPresetsDto

    @DELETE("portions/{barcode}/presets")
    suspend fun deletePresets(@Path("barcode") barcode: String): Response<Unit>
}
