package com.example.travelpractice

import retrofit2.http.GET
import retrofit2.http.Query

data class OMCurrent(val temperature_2m: Double?, val weather_code: Int?)
data class OMDaily(
    val time: List<String>?,
    val temperature_2m_max: List<Double>?,
    val temperature_2m_min: List<Double>?,
    val precipitation_probability_max: List<Int>? // since you're requesting it
)

data class OMHourly(
    val time: List<String>?,
    val precipitation_probability: List<Int>?,
    val rain: List<Double>?,
    val showers: List<Double>?,
    val snowfall: List<Double>?,
    val weather_code: List<Int>?,
    val cloud_cover: List<Int>?
)

data class OMResponse(
    val current: OMCurrent?,
    val daily: OMDaily?,
    val hourly: OMHourly?
)

interface OpenMeteoService {
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,

        @Query("current") current: String = "temperature_2m,weather_code",
        @Query("daily") daily: String =
            "temperature_2m_max,temperature_2m_min,weather_code,precipitation_probability_max,rain_sum,showers_sum,snowfall_sum",

        @Query("hourly") hourly: String =
            "precipitation_probability,rain,showers,snowfall,weather_code",

        @Query("forecast_days") days: Int = 7,
        @Query("timezone") tz: String = "auto",
        @Query("temperature_unit") unit: String = "fahrenheit"
    ): OMResponse
}






