package com.example.travelpractice

import retrofit2.http.GET
import retrofit2.http.Query

data class OWMain(
    val temp: Double?,
    val temp_min: Double?,
    val temp_max: Double?
)

data class OWWeather(
    val id: Int?,
    val main: String?,
    val description: String?
)

data class OWCurrent(
    val main: OWMain?,
    val weather: List<OWWeather>?
)

data class OWForecastItem(
    val dt: Long?,
    val dt_txt: String?,
    val main: OWMain?,
    val weather: List<OWWeather>?,
    val pop: Double?, // precipitation probability (0-1)
    val rain: OWRain?,
    val snow: OWSnow?,
    val clouds: OWClouds?
)

data class OWRain(val `3h`: Double?)
data class OWSnow(val `3h`: Double?)
data class OWClouds(val all: Int?)

data class OWForecastResponse(
    val list: List<OWForecastItem>?
)

interface OpenWeatherService {
    @GET("data/2.5/weather")
    suspend fun current(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "imperial"
    ): OWCurrent

    @GET("data/2.5/forecast")
    suspend fun forecast(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "imperial"
    ): OWForecastResponse
}