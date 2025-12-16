package com.example.travelpractice.weather

import com.example.travelpractice.NominatimService
import com.example.travelpractice.OpenMeteoService
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object HttpClients {
    // Create a shared OkHttpClient with proper timeouts
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Open-Meteo client
    val openMeteo: OpenMeteoService = Retrofit.Builder()
        .baseUrl("https://api.open-meteo.com/")
        .client(okHttpClient)  // Add this line
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OpenMeteoService::class.java)

    // Nominatim client
    val nominatim: NominatimService = Retrofit.Builder()
        .baseUrl("https://nominatim.openstreetmap.org/")
        .client(okHttpClient)  // Add this line
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(NominatimService::class.java)
}