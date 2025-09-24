package com.example.travelpractice.weather

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object HttpClients {
    // Open-Meteo client
    val openMeteo: OpenMeteoService = Retrofit.Builder()
        .baseUrl("https://api.open-meteo.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OpenMeteoService::class.java)

    // Nominatim client
    val nominatim: NominatimService = Retrofit.Builder()
        .baseUrl("https://nominatim.openstreetmap.org/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(NominatimService::class.java)
}


