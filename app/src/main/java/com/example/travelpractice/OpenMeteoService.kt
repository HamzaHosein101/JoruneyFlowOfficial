package com.example.travelpractice.weather

import retrofit2.http.GET
import retrofit2.http.Query

data class OMCurrent(val temperature_2m: Double?, val weather_code: Int?)
data class OMDaily(
    val time: List<String>?,
    val temperature_2m_max: List<Double>?,
    val temperature_2m_min: List<Double>?,
    val weather_code: List<Int>?
)
data class OMResponse(
    val current: OMCurrent?,
    val daily: OMDaily?
)

interface OpenMeteoService {
    // OpenMeteoService.kt
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current") current: String = "temperature_2m,weather_code",
        @Query("daily") daily: String = "temperature_2m_max,temperature_2m_min,weather_code",
        @Query("forecast_days") days: Int = 7,
        @Query("timezone") tz: String = "auto",
        @Query("temperature_unit") unit: String = "celsius"
    ): OMResponse

}





